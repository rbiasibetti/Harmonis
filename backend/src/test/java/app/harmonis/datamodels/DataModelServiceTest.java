package app.harmonis.datamodels;

import app.harmonis.datamodels.DataModelDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DataModelServiceTest {

    private final Map<Long, DataModel> db = new HashMap<>();
    private final List<DataModelRow> rowDb = new ArrayList<>();
    private long seq = 0, rowSeq = 0;
    private DataModelRepository models;
    private DataModelRowRepository rows;
    private DataModelService service;

    @BeforeEach
    void setUp() {
        models = mock(DataModelRepository.class);
        rows = mock(DataModelRowRepository.class);
        when(models.save(any(DataModel.class))).thenAnswer(i -> {
            DataModel m = i.getArgument(0);
            if (m.getId() == null) setId(m, ++seq);
            db.put(m.getId(), m);
            return m;
        });
        when(models.findById(any())).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));
        when(models.existsByNameIgnoreCase(any())).thenAnswer(i ->
                db.values().stream().anyMatch(m -> m.getName().equalsIgnoreCase(i.getArgument(0))));
        when(rows.save(any(DataModelRow.class))).thenAnswer(i -> {
            DataModelRow r = i.getArgument(0);
            rowDb.add(r);
            return r;
        });
        when(rows.countByDataModelId(anyLong())).thenAnswer(i ->
                rowDb.stream().filter(r -> r.getDataModelId().equals(i.getArgument(0))).count());
        when(rows.findByDataModelIdOrderByIdAsc(anyLong())).thenAnswer(i ->
                rowDb.stream().filter(r -> r.getDataModelId().equals(i.getArgument(0))).toList());
        doAnswer(i -> { rowDb.removeIf(r -> r.getDataModelId().equals(i.getArgument(0))); return null; })
                .when(rows).deleteByDataModelId(anyLong());
        service = new DataModelService(models, rows, new ObjectMapper());
    }

    private static void setId(Object o, long id) {
        try { var f = o.getClass().getDeclaredField("id"); f.setAccessible(true); f.set(o, id); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private SaveDataModelRequest staticReq(String name) {
        return new SaveDataModelRequest(name, "d", "static", null,
                List.of(new ColumnSpec("cod", "Código", "number"), new ColumnSpec("nome", "Nome", "text")),
                List.of(new FilterSpec("nome", "Nome", null)), "nome");
    }

    @Test
    void criaValidaEResolveLabelKeyETipoDoFiltro() {
        DataModelView v = service.create(staticReq("Clientes"), 1L);
        assertEquals("nome", v.labelKey());
        assertEquals(2, v.columns().size());
        assertEquals("text", v.filters().get(0).type(), "tipo do filtro vem da coluna");
        assertEquals("draft", v.status());
    }

    @Test
    void nomeDuplicadoEColunaInvalidaSaoRejeitados() {
        service.create(staticReq("Clientes"), 1L);
        assertThrows(ResponseStatusException.class, () -> service.create(staticReq("clientes"), 1L));
        SaveDataModelRequest bad = new SaveDataModelRequest("X", null, "static", null,
                List.of(new ColumnSpec("1nome", "x", "text")), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> service.create(bad, 1L));
        SaveDataModelRequest noCols = new SaveDataModelRequest("Y", null, "static", null, List.of(), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> service.create(noCols, 1L));
    }

    @Test
    void filtroDeveReferenciarColunaExistente() {
        SaveDataModelRequest bad = new SaveDataModelRequest("Z", null, "static", null,
                List.of(new ColumnSpec("nome", "Nome", "text")),
                List.of(new FilterSpec("inexistente", "X", null)), null);
        assertThrows(ResponseStatusException.class, () -> service.create(bad, 1L));
    }

    @Test
    void consultaSqlSoAceitaSelectESemComandosProibidos() {
        var ok = new SaveDataModelRequest("Prod", null, "query", "SELECT id AS cod, nome FROM produtos",
                List.of(new ColumnSpec("cod", "Cód", "number"), new ColumnSpec("nome", "Nome", "text")), List.of(), null);
        DataModelView v = service.create(ok, 1L);
        assertEquals("query", v.sourceType());
        assertEquals(-1, v.rows(), "query não tem contagem de linhas");

        var del = new SaveDataModelRequest("P2", null, "query", "DELETE FROM produtos",
                List.of(new ColumnSpec("cod", "c", "text")), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> service.create(del, 1L));
        var multi = new SaveDataModelRequest("P3", null, "query", "SELECT 1; DROP TABLE x",
                List.of(new ColumnSpec("cod", "c", "text")), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> service.create(multi, 1L));
    }

    @Test
    void publicarExigeColunasEEntraNasOpcoes() {
        DataModelView v = service.create(staticReq("Clientes"), 1L);
        when(models.findByStatusOrderByNameAsc("published")).thenAnswer(i ->
                db.values().stream().filter(DataModel::isPublished).toList());
        assertTrue(service.options().isEmpty());
        service.setPublished(v.id(), true);
        assertEquals(1, service.options().size());
        assertEquals("nome", service.options().get(0).labelKey());
    }

    @Test
    void importaCsvMapeandoPorRotuloOuKeySemAcento() {
        DataModelView v = service.create(staticReq("Clientes"), 1L);
        String csv = "Código;Nome\n1;Maria\n2;João\n";
        var file = new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        ImportResult r = service.importCsv(v.id(), file);
        assertEquals(2, r.imported());
        List<Map<String, Object>> rowsOut = service.rows(v.id());
        assertEquals("Maria", rowsOut.get(0).get("nome"));
        assertEquals("1", rowsOut.get(0).get("cod"));
    }

    @Test
    void importarEmModeloDeConsultaFalha() {
        var q = new SaveDataModelRequest("Q", null, "query", "SELECT nome FROM t",
                List.of(new ColumnSpec("nome", "Nome", "text")), List.of(), null);
        DataModelView v = service.create(q, 1L);
        var file = new MockMultipartFile("file", "c.csv", "text/csv", "nome\nx\n".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.importCsv(v.id(), file));
    }
}
