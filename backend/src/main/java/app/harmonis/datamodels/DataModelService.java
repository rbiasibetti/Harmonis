package app.harmonis.datamodels;

import app.harmonis.datamodels.DataModelDtos.*;
import app.harmonis.identity.AclCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** CRUD, validacao, publicacao, importacao de CSV e edicao de linhas dos modelos de dados. */
@Service
public class DataModelService {

    private static final Set<String> TYPES = Set.of("text", "number", "date");
    private static final Pattern KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    // Palavras proibidas na consulta (defesa em profundidade; a seguranca principal e envolver
    // a consulta como subquery, que so aceita SELECT).
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|exec|execute|merge|grant|revoke|create|into|sp_|xp_)\\b",
            Pattern.CASE_INSENSITIVE);

    private final DataModelRepository models;
    private final DataModelRowRepository rows;
    private final ObjectMapper mapper;

    public DataModelService(DataModelRepository models, DataModelRowRepository rows, ObjectMapper mapper) {
        this.models = models;
        this.rows = rows;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<DataModelSummary> list() {
        return models.findAllByOrderByNameAsc().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public DataModelView get(long id) {
        return toView(require(id));
    }

    /** Opcoes para o construtor/preenchimento: somente modelos publicados. */
    @Transactional(readOnly = true)
    public List<DataModelOption> options() {
        return models.findByStatusOrderByNameAsc("published").stream()
                .map(m -> new DataModelOption(m.getId(), m.getName(),
                        readColumns(m), readFilters(m), labelKeyOf(m)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DataModelOption option(long id) {
        DataModel m = require(id);
        return new DataModelOption(m.getId(), m.getName(), readColumns(m), readFilters(m), labelKeyOf(m));
    }

    @Transactional
    public DataModelView create(SaveDataModelRequest req, Long userId) {
        DataModel m = new DataModel();
        apply(m, req, true);
        m.setStatus("draft");
        m.setCreatedBy(userId);
        models.save(m);
        return toView(m);
    }

    @Transactional
    public DataModelView update(long id, SaveDataModelRequest req) {
        DataModel m = require(id);
        apply(m, req, false);
        models.save(m);
        return toView(m);
    }

    @Transactional
    public void delete(long id) {
        DataModel m = require(id);
        rows.deleteByDataModelId(m.getId());
        models.delete(m);
    }

    @Transactional
    public DataModelView setPublished(long id, boolean published) {
        DataModel m = require(id);
        if (published) {
            if (readColumns(m).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Defina ao menos uma coluna antes de publicar.");
            }
            if (m.isQuery() && (m.getQuerySql() == null || m.getQuerySql().isBlank())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Informe a consulta SQL antes de publicar.");
            }
        }
        m.setStatus(published ? "published" : "draft");
        models.save(m);
        return toView(m);
    }

    // ----- linhas (static) -----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rows(long id) {
        DataModel m = require(id);
        requireStatic(m);
        return rows.findByDataModelIdOrderByIdAsc(id).stream().map(r -> readData(r.getDataJson())).toList();
    }

    /** Substitui todas as linhas (edicao manual). */
    @Transactional
    public void replaceRows(long id, List<Map<String, Object>> newRows) {
        DataModel m = require(id);
        requireStatic(m);
        List<ColumnSpec> cols = readColumns(m);
        rows.deleteByDataModelId(id);
        if (newRows != null) {
            for (Map<String, Object> raw : newRows) saveRow(m, project(raw, cols));
        }
    }

    /** Importa linhas de um CSV (append). Cabecalho mapeado por rotulo ou key das colunas. */
    @Transactional
    public ImportResult importCsv(long id, MultipartFile file) {
        DataModel m = require(id);
        requireStatic(m);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo CSV vazio.");
        }
        List<ColumnSpec> cols = readColumns(m);
        String text;
        try { text = new String(file.getBytes(), StandardCharsets.UTF_8).replace("﻿", ""); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o CSV."); }

        List<List<String>> table = Csv.parse(text);
        if (table.isEmpty()) return new ImportResult(0);

        List<String> header = table.get(0);
        // mapeia cada coluna do CSV para uma coluna do modelo (por key ou rotulo, sem acento/caixa)
        Map<Integer, String> colAt = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = norm(header.get(i));
            for (ColumnSpec c : cols) {
                if (norm(c.key()).equals(h) || norm(c.label()).equals(h)) { colAt.put(i, c.key()); break; }
            }
        }
        if (colAt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nenhuma coluna do CSV corresponde às colunas do modelo.");
        }
        int imported = 0;
        for (int r = 1; r < table.size(); r++) {
            List<String> line = table.get(r);
            if (line.stream().allMatch(s -> s == null || s.isBlank())) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            colAt.forEach((idx, key) -> data.put(key, idx < line.size() ? line.get(idx) : null));
            saveRow(m, data);
            imported++;
        }
        return new ImportResult(imported);
    }

    // ----- helpers de dominio -----

    DataModel require(long id) {
        return models.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modelo de dados não encontrado."));
    }

    private void requireStatic(DataModel m) {
        if (m.isQuery()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este modelo é de consulta SQL; não possui linhas editáveis.");
        }
    }

    private void apply(DataModel m, SaveDataModelRequest req, boolean creating) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) throw bad("Informe um nome.");
        boolean nameTaken = models.existsByNameIgnoreCase(name)
                && (creating || !name.equalsIgnoreCase(m.getName()));
        if (nameTaken) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um modelo com esse nome.");

        String source = "query".equals(req.sourceType()) ? "query" : "static";
        List<ColumnSpec> cols = validColumns(req.columns());
        List<FilterSpec> filters = validFilters(req.filters(), cols);
        String labelKey = resolveLabelKey(req.labelKey(), cols);

        m.setName(name);
        m.setDescription(blankToNull(req.description()));
        m.setSourceType(source);
        m.setColumnsJson(write(cols));
        m.setFiltersJson(write(filters));
        m.setLabelKey(labelKey);
        if ("query".equals(source)) {
            m.setQuerySql(validQuery(req.querySql()));
        } else {
            m.setQuerySql(null);
        }
    }

    private List<ColumnSpec> validColumns(List<ColumnSpec> input) {
        if (input == null || input.isEmpty()) throw bad("Defina ao menos uma coluna.");
        Set<String> keys = new HashSet<>();
        List<ColumnSpec> out = new ArrayList<>();
        for (ColumnSpec c : input) {
            String key = c.key() == null ? "" : c.key().trim();
            if (!KEY.matcher(key).matches()) throw bad("Chave de coluna inválida: \"" + c.key() + "\".");
            if (!keys.add(key.toLowerCase())) throw bad("Coluna repetida: \"" + key + "\".");
            String label = c.label() == null || c.label().isBlank() ? key : c.label().trim();
            String type = TYPES.contains(c.type()) ? c.type() : "text";
            out.add(new ColumnSpec(key, label, type));
        }
        return out;
    }

    private List<FilterSpec> validFilters(List<FilterSpec> input, List<ColumnSpec> cols) {
        if (input == null) return List.of();
        Map<String, ColumnSpec> byKey = new HashMap<>();
        cols.forEach(c -> byKey.put(c.key(), c));
        List<FilterSpec> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FilterSpec f : input) {
            ColumnSpec col = byKey.get(f.key());
            if (col == null) throw bad("Filtro referencia uma coluna inexistente: \"" + f.key() + "\".");
            if (!seen.add(f.key())) continue;
            String label = f.label() == null || f.label().isBlank() ? col.label() : f.label().trim();
            out.add(new FilterSpec(col.key(), label, col.type())); // tipo sempre da coluna
        }
        return out;
    }

    private String resolveLabelKey(String labelKey, List<ColumnSpec> cols) {
        if (labelKey != null && cols.stream().anyMatch(c -> c.key().equals(labelKey))) return labelKey;
        return cols.get(0).key();
    }

    private String validQuery(String sql) {
        String q = sql == null ? "" : sql.trim();
        if (q.endsWith(";")) q = q.substring(0, q.length() - 1).trim();
        if (q.isEmpty()) throw bad("Informe a consulta SQL.");
        String lower = q.toLowerCase();
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw bad("A consulta deve ser um SELECT.");
        }
        if (q.contains(";")) throw bad("A consulta deve ser um único comando (sem ';').");
        if (FORBIDDEN.matcher(q).find()) throw bad("A consulta só pode ler dados (SELECT).");
        return q;
    }

    private void saveRow(DataModel m, Map<String, Object> data) {
        DataModelRow r = new DataModelRow();
        r.setDataModelId(m.getId());
        r.setDataJson(write(data));
        rows.save(r);
    }

    /** Mantem apenas as colunas definidas (descarta chaves estranhas). */
    private static Map<String, Object> project(Map<String, Object> raw, List<ColumnSpec> cols) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (ColumnSpec c : cols) if (raw.containsKey(c.key())) out.put(c.key(), raw.get(c.key()));
        return out;
    }

    private DataModelSummary toSummary(DataModel m) {
        long rowCount = m.isQuery() ? -1 : rows.countByDataModelId(m.getId());
        return new DataModelSummary(m.getId(), m.getName(), m.getDescription(), m.getSourceType(),
                m.getStatus(), readColumns(m).size(), rowCount);
    }

    private DataModelView toView(DataModel m) {
        long rowCount = m.isQuery() ? -1 : rows.countByDataModelId(m.getId());
        return new DataModelView(m.getId(), m.getName(), m.getDescription(), m.getSourceType(),
                m.getQuerySql(), readColumns(m), readFilters(m), labelKeyOf(m), m.getStatus(), rowCount);
    }

    List<ColumnSpec> readColumns(DataModel m) {
        if (m.getColumnsJson() == null || m.getColumnsJson().isBlank()) return List.of();
        try { return mapper.readValue(m.getColumnsJson(), new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    List<FilterSpec> readFilters(DataModel m) {
        if (m.getFiltersJson() == null || m.getFiltersJson().isBlank()) return List.of();
        try { return mapper.readValue(m.getFiltersJson(), new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    String labelKeyOf(DataModel m) {
        if (m.getLabelKey() != null && !m.getLabelKey().isBlank()) return m.getLabelKey();
        List<ColumnSpec> c = readColumns(m);
        return c.isEmpty() ? null : c.get(0).key();
    }

    private Map<String, Object> readData(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw bad("Dados inválidos."); }
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }

    /** Catalogo de permissoes exposto para o front (reaproveita o grupo do AclCatalog). */
    public static List<String> permissions() {
        return AclCatalog.GROUPS.getOrDefault("Modelos de dados", List.of());
    }
}
