package app.harmonis.datamodels;

import app.harmonis.datamodels.DataModelDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;

/**
 * Executa a consulta de um modelo de dados no preenchimento: aplica os filtros (fixos por tipo,
 * sem diferenciar maiusculas) e pagina. Fonte static -> linhas do banco filtradas em memoria;
 * fonte query -> SELECT do administrador envolvido como subconsulta, com filtros parametrizados
 * e paginacao no SQL Server. O SELECT so pode ler dados (garantido pela subconsulta + validacao).
 */
@Service
public class DataModelQueryService {

    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_SIZE = 10;

    private final DataModelService service;
    private final DataModelRowRepository rows;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public DataModelQueryService(DataModelService service, DataModelRowRepository rows,
                                 ObjectMapper mapper, DataSource dataSource) {
        this.service = service;
        this.rows = rows;
        this.mapper = mapper;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional(readOnly = true)
    public QueryResult query(long id, QueryRequest req) {
        DataModel m = service.require(id);
        List<ColumnSpec> cols = service.readColumns(m);
        List<FilterSpec> filters = service.readFilters(m);
        Map<String, Object> values = req == null || req.filters() == null ? Map.of() : req.filters();
        int page = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
        int size = req == null || req.size() == null ? DEFAULT_SIZE : Math.min(Math.max(req.size(), 1), MAX_SIZE);

        // filtros ativos (valor informado)
        List<FilterSpec> active = filters.stream()
                .filter(f -> notBlank(values.get(f.key())))
                .toList();

        return m.isQuery()
                ? querySource(m, cols, active, values, page, size)
                : staticSource(m, cols, active, values, page, size);
    }

    // ----- fonte static (linhas em memoria) -----

    private QueryResult staticSource(DataModel m, List<ColumnSpec> cols, List<FilterSpec> active,
                                     Map<String, Object> values, int page, int size) {
        List<Map<String, Object>> all = rows.findByDataModelIdOrderByIdAsc(m.getId()).stream()
                .map(r -> read(r.getDataJson()))
                .filter(row -> active.stream().allMatch(f -> matches(f, row.get(f.key()), values.get(f.key()))))
                .toList();
        long total = all.size();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> pageRows = all.subList(from, to).stream().map(r -> project(r, cols)).toList();
        return new QueryResult(pageRows, page, size, total, false);
    }

    private static boolean matches(FilterSpec f, Object cell, Object filterValue) {
        String fv = filterValue == null ? "" : filterValue.toString().trim();
        if (fv.isEmpty()) return true;
        String cv = cell == null ? "" : cell.toString().trim();
        switch (f.type()) {
            case "number" -> {
                Double a = toDouble(cv), b = toDouble(fv);
                return a != null && b != null && a.doubleValue() == b.doubleValue();
            }
            case "date" -> {
                return cv.length() >= 10 && cv.substring(0, 10).equals(fv.length() >= 10 ? fv.substring(0, 10) : fv);
            }
            default -> {
                return cv.toLowerCase(Locale.ROOT).contains(fv.toLowerCase(Locale.ROOT));
            }
        }
    }

    // ----- fonte query (SQL) -----

    private QueryResult querySource(DataModel m, List<ColumnSpec> cols, List<FilterSpec> active,
                                    Map<String, Object> values, int page, int size) {
        String inner = m.getQuerySql();
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (FilterSpec f : active) {
            String col = "q." + bracket(f.key());
            Object v = values.get(f.key());
            if (where.length() > 0) where.append(" AND ");
            switch (f.type()) {
                case "number" -> {
                    Double d = toDouble(v.toString());
                    if (d == null) { where.append("1=0"); } else { where.append(col).append(" = ?"); params.add(d); }
                }
                case "date" -> {
                    where.append("CAST(").append(col).append(" AS DATE) = ?");
                    params.add(parseDate(v.toString()));
                }
                default -> {
                    where.append("LOWER(CAST(").append(col).append(" AS NVARCHAR(4000))) LIKE ?");
                    params.add("%" + v.toString().trim().toLowerCase(Locale.ROOT) + "%");
                }
            }
        }
        String whereSql = where.length() == 0 ? "" : " WHERE " + where;
        String colList = cols.isEmpty() ? "*"
                : String.join(", ", cols.stream().map(c -> "q." + bracket(c.key())).toList());

        String countSql = "SELECT COUNT(*) FROM (" + inner + ") q" + whereSql;
        String pageSql = "SELECT " + colList + " FROM (" + inner + ") q" + whereSql
                + " ORDER BY (SELECT 1) OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try {
            Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(page * size);
            pageParams.add(size);
            List<Map<String, Object>> list = jdbc.query(pageSql, (rs, i) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                for (ColumnSpec c : cols) row.put(c.key(), rs.getObject(c.key()));
                return row;
            }, pageParams.toArray());
            return new QueryResult(list, page, size, total == null ? 0 : total, false);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Falha ao consultar o modelo de dados. Verifique a consulta SQL e as colunas declaradas.");
        }
    }

    // ----- helpers -----

    private static Map<String, Object> project(Map<String, Object> raw, List<ColumnSpec> cols) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ColumnSpec c : cols) out.put(c.key(), raw.get(c.key()));
        return out;
    }

    private static String bracket(String key) {
        // key ja validada (A-Za-z0-9_) na criacao; defensivo mesmo assim
        return "[" + key.replace("]", "") + "]";
    }

    private static boolean notBlank(Object o) { return o != null && !o.toString().trim().isEmpty(); }

    private static Double toDouble(String s) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); }
        catch (Exception e) { return null; }
    }

    private static java.sql.Date parseDate(String s) {
        try { return java.sql.Date.valueOf(LocalDate.parse(s.trim().substring(0, 10))); }
        catch (Exception e) { return null; }
    }

    private Map<String, Object> read(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }
}
