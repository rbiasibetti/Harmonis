package app.harmonis.datamodels;

import app.harmonis.datamodels.DataModelDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DataModelQueryServiceTest {

    private DataModelService service;
    private DataModelRowRepository rows;
    private DataModelQueryService query;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(DataModelService.class);
        rows = mock(DataModelRowRepository.class);
        query = new DataModelQueryService(service, rows, mapper, mock(DataSource.class));

        DataModel m = new DataModel();
        setId(m, 1L);
        m.setSourceType("static");
        when(service.require(1L)).thenReturn(m);
        when(service.readColumns(m)).thenReturn(List.of(
                new ColumnSpec("cod", "Código", "number"),
                new ColumnSpec("nome", "Nome", "text"),
                new ColumnSpec("nasc", "Nascimento", "date")));
        when(service.readFilters(m)).thenReturn(List.of(
                new FilterSpec("nome", "Nome", "text"),
                new FilterSpec("cod", "Código", "number"),
                new FilterSpec("nasc", "Nascimento", "date")));

        List<DataModelRow> db = new ArrayList<>();
        db.add(row(1, "{\"cod\":10,\"nome\":\"Maria Silva\",\"nasc\":\"1990-05-17\"}"));
        db.add(row(2, "{\"cod\":20,\"nome\":\"João Souza\",\"nasc\":\"1985-01-02\"}"));
        db.add(row(3, "{\"cod\":30,\"nome\":\"MARIANA Lima\",\"nasc\":\"1990-05-17\"}"));
        when(rows.findByDataModelIdOrderByIdAsc(anyLong())).thenReturn(db);
    }

    private static DataModelRow row(long id, String json) {
        DataModelRow r = new DataModelRow();
        setId(r, id); r.setDataModelId(1L); r.setDataJson(json);
        return r;
    }
    private static void setId(Object o, long id) {
        try { var f = o.getClass().getDeclaredField("id"); f.setAccessible(true); f.set(o, id); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private QueryResult q(Map<String, Object> filters) {
        return query.query(1L, new QueryRequest(filters, 0, 10));
    }

    @Test
    void textoContemSemDiferenciarMaiusculas() {
        QueryResult r = q(Map.of("nome", "maria"));
        assertEquals(2, r.total()); // "Maria Silva" e "MARIANA Lima"
    }

    @Test
    void numeroIgual() {
        QueryResult r = q(Map.of("cod", "20"));
        assertEquals(1, r.total());
        assertEquals("João Souza", r.rows().get(0).get("nome"));
    }

    @Test
    void dataIgual() {
        QueryResult r = q(Map.of("nasc", "1990-05-17"));
        assertEquals(2, r.total());
    }

    @Test
    void filtrosCombinamComE() {
        QueryResult r = q(Map.of("nome", "mari", "nasc", "1990-05-17"));
        assertEquals(2, r.total());
        QueryResult r2 = q(Map.of("nome", "joao"));
        assertEquals(0, r2.total(), "acento difere: 'joao' não casa 'João'");
    }

    @Test
    void semFiltroRetornaTudoEProjetaColunas() {
        QueryResult r = q(Map.of());
        assertEquals(3, r.total());
        assertTrue(r.rows().get(0).keySet().containsAll(List.of("cod", "nome", "nasc")));
    }

    @Test
    void paginacao() {
        QueryResult r = query.query(1L, new QueryRequest(Map.of(), 0, 2));
        assertEquals(2, r.rows().size());
        assertEquals(3, r.total());
        QueryResult p2 = query.query(1L, new QueryRequest(Map.of(), 1, 2));
        assertEquals(1, p2.rows().size());
    }
}
