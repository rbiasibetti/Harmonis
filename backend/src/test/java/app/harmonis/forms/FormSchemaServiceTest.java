package app.harmonis.forms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormSchemaServiceTest {

    private FormSchemaService svc;

    @BeforeEach
    void setUp() {
        svc = new FormSchemaService(new ObjectMapper());
    }

    private FormField field(String type, String label, boolean required, List<String> options) {
        return new FormField(null, null, type, label, null, null, required, options);
    }

    @Test
    void normaliza_gera_key_e_id_unicos() {
        List<FormField> out = svc.normalize(List.of(
                field("text", "Nome", true, null),
                field("text", "Sobrenome", false, null)));
        assertEquals(2, out.size());
        assertNotNull(out.get(0).key());
        assertTrue(out.get(0).key().startsWith("campo_"));
        assertNotEquals(out.get(0).key(), out.get(1).key());
        assertTrue(out.get(0).id().startsWith("f_"));
    }

    @Test
    void tipo_invalido_e_rejeitado() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> svc.normalize(List.of(field("slider", "X", false, null))));
        assertTrue(ex.getReason().contains("inválido"));
    }

    @Test
    void select_sem_opcoes_e_rejeitado() {
        assertThrows(ResponseStatusException.class,
                () -> svc.normalize(List.of(field("select", "UF", true, List.of()))));
    }

    @Test
    void campo_sem_rotulo_e_rejeitado() {
        assertThrows(ResponseStatusException.class,
                () -> svc.normalize(List.of(field("text", "  ", false, null))));
    }

    @Test
    void valida_obrigatorios_e_tipos() {
        List<FormField> fields = svc.normalize(List.of(
                new FormField(null, "nome", "text", "Nome", null, null, true, null),
                new FormField(null, "valor", "number", "Valor", null, null, true, null),
                new FormField(null, "email", "email", "E-mail", null, null, false, null),
                new FormField(null, "uf", "select", "UF", null, null, true, List.of("SP", "RJ"))));

        // tudo vazio: nome, valor e uf obrigatorios
        Map<String, String> e1 = svc.validate(fields, Map.of());
        assertTrue(e1.containsKey("nome"));
        assertTrue(e1.containsKey("valor"));
        assertTrue(e1.containsKey("uf"));
        assertFalse(e1.containsKey("email"));

        // tipos invalidos
        Map<String, String> e2 = svc.validate(fields, Map.of(
                "nome", "Ana", "valor", "abc", "email", "nao-email", "uf", "MG"));
        assertFalse(e2.containsKey("nome"));
        assertTrue(e2.containsKey("valor"));
        assertTrue(e2.containsKey("email"));
        assertTrue(e2.containsKey("uf"));

        // tudo valido
        Map<String, String> e3 = svc.validate(fields, Map.of(
                "nome", "Ana", "valor", "1200", "email", "ana@acme.test", "uf", "SP"));
        assertTrue(e3.isEmpty());
    }

    @Test
    void checkbox_obrigatorio_precisa_ser_marcado() {
        List<FormField> fields = svc.normalize(List.of(
                new FormField(null, "aceite", "checkbox", "Aceito os termos", null, null, true, null)));

        assertTrue(svc.validate(fields, Map.of("aceite", false)).containsKey("aceite"));
        assertTrue(svc.validate(fields, Map.of()).containsKey("aceite"));
        assertFalse(svc.validate(fields, Map.of("aceite", true)).containsKey("aceite"));
    }

    @Test
    void round_trip_de_serializacao_preserva_campos() {
        String json = svc.writeFields(List.of(
                new FormField(null, "nome", "text", "Nome", null, null, true, null)));
        assertTrue(json.contains("\"fields\""));
        List<FormField> back = svc.readFields(json);
        assertEquals(1, back.size());
        assertEquals("Nome", back.get(0).label());
        assertEquals("nome", back.get(0).key());
    }
}
