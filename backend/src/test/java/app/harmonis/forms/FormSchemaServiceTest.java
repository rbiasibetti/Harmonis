package app.harmonis.forms;

import app.harmonis.forms.FormField.FieldCondition;
import app.harmonis.forms.FormField.Validation;
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
        return new FormField(null, null, type, label, null, null, required, options, null, null, null, null, null, null);
    }

    private FormField field(String key, String type, boolean required, List<String> options,
                            Validation v, FieldCondition vw) {
        return new FormField(null, key, type, key, null, null, required, options, null, null, v, vw, null, null);
    }

    private FormField roleField(String key, String type, boolean required, String fillRole) {
        return new FormField(null, key, type, key, null, null, required, null, null, null, null, null, fillRole, null);
    }

    private Validation val() {
        return new Validation(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // ----- normalizacao -----

    @Test
    void normaliza_gera_key_e_id_unicos() {
        List<FormField> out = svc.normalize(List.of(
                field("text", "Nome", true, null),
                field("text", "Sobrenome", false, null)));
        assertEquals(2, out.size());
        assertTrue(out.get(0).key().startsWith("campo_"));
        assertNotEquals(out.get(0).key(), out.get(1).key());
        assertEquals("full", out.get(0).width());
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
    void secao_nao_tem_key_e_nao_exige_opcoes() {
        List<FormField> out = svc.normalize(List.of(field("section", "Dados pessoais", false, null)));
        assertEquals(1, out.size());
        assertNull(out.get(0).key());
        assertEquals("section", out.get(0).type());
    }

    @Test
    void display_normalizado_por_tipo() {
        FormField single = new FormField(null, null, "select", "UF", null, null, false, List.of("SP"), null, null, null, null, null, null);
        FormField radioDisplay = new FormField(null, null, "select", "UF", null, null, false, List.of("SP"), "radio", null, null, null, null, null);
        FormField multi = new FormField(null, null, "multiselect", "Tags", null, null, false, List.of("A"), null, null, null, null, null, null);

        assertEquals("combo", svc.normalize(List.of(single)).get(0).display());
        assertEquals("radio", svc.normalize(List.of(radioDisplay)).get(0).display());
        assertEquals("combo", svc.normalize(List.of(multi)).get(0).display());
    }

    // ----- validacao basica -----

    @Test
    void valida_obrigatorios_e_tipos() {
        List<FormField> fields = svc.normalize(List.of(
                field("nome", "text", true, null, null, null),
                field("valor", "number", true, null, null, null),
                field("uf", "select", true, List.of("SP", "RJ"), null, null)));

        Map<String, String> e1 = svc.validate(fields, Map.of());
        assertTrue(e1.containsKey("nome"));
        assertTrue(e1.containsKey("valor"));
        assertTrue(e1.containsKey("uf"));

        Map<String, String> e2 = svc.validate(fields, Map.of("nome", "Ana", "valor", "abc", "uf", "MG"));
        assertTrue(e2.containsKey("valor"));
        assertTrue(e2.containsKey("uf"));

        assertTrue(svc.validate(fields, Map.of("nome", "Ana", "valor", "10", "uf", "SP")).isEmpty());
    }

    @Test
    void secao_e_ignorada_na_validacao() {
        List<FormField> fields = svc.normalize(List.of(field("section", "Bloco", false, null)));
        assertTrue(svc.validate(fields, Map.of()).isEmpty());
    }

    // ----- novas validacoes -----

    @Test
    void numero_positivo() {
        Validation v = new Validation(null, null, null, null, true, null, null, null, null, null, null, null, null, null);
        List<FormField> fields = svc.normalize(List.of(field("valor", "number", true, null, v, null)));
        assertTrue(svc.validate(fields, Map.of("valor", "-5")).containsKey("valor"));
        assertTrue(svc.validate(fields, Map.of("valor", "0")).containsKey("valor"));
        assertFalse(svc.validate(fields, Map.of("valor", "5")).containsKey("valor"));
    }

    @Test
    void numero_min_max_e_inteiro() {
        Validation v = new Validation(null, null, 10.0, 100.0, null, true, null, null, null, null, null, null, null, null);
        List<FormField> fields = svc.normalize(List.of(field("qtd", "number", true, null, v, null)));
        assertTrue(svc.validate(fields, Map.of("qtd", "5")).containsKey("qtd"));     // < min
        assertTrue(svc.validate(fields, Map.of("qtd", "150")).containsKey("qtd"));   // > max
        assertTrue(svc.validate(fields, Map.of("qtd", "20.5")).containsKey("qtd"));  // nao inteiro
        assertFalse(svc.validate(fields, Map.of("qtd", "20")).containsKey("qtd"));
    }

    @Test
    void regex_com_mensagem_custom() {
        Validation v = new Validation("\\d{5}-\\d{3}", "CEP inválido", null, null, null, null, null, null, null, null, null, null, null, null);
        List<FormField> fields = svc.normalize(List.of(field("cep", "text", true, null, v, null)));
        Map<String, String> err = svc.validate(fields, Map.of("cep", "abc"));
        assertEquals("CEP inválido", err.get("cep"));
        assertFalse(svc.validate(fields, Map.of("cep", "12345-678")).containsKey("cep"));
    }

    @Test
    void tamanho_minimo_de_texto() {
        Validation v = new Validation(null, null, null, null, null, null, 3, 10, null, null, null, null, null, null);
        List<FormField> fields = svc.normalize(List.of(field("apelido", "text", true, null, v, null)));
        assertTrue(svc.validate(fields, Map.of("apelido", "ab")).containsKey("apelido"));
        assertFalse(svc.validate(fields, Map.of("apelido", "abcd")).containsKey("apelido"));
    }

    @Test
    void data_apos_outro_campo() {
        Validation v = new Validation(null, null, null, null, null, null, null, null, null, null, "inicio", null, null, null);
        List<FormField> fields = svc.normalize(List.of(
                field("inicio", "date", true, null, null, null),
                field("fim", "date", true, null, v, null)));
        // fim antes de inicio -> erro
        assertTrue(svc.validate(fields, Map.of("inicio", "2026-05-10", "fim", "2026-05-01")).containsKey("fim"));
        // fim depois de inicio -> ok
        assertFalse(svc.validate(fields, Map.of("inicio", "2026-05-10", "fim", "2026-05-20")).containsKey("fim"));
    }

    @Test
    void multiselect_valida_opcoes_e_obrigatoriedade() {
        List<FormField> fields = svc.normalize(List.of(
                field("tags", "multiselect", true, List.of("A", "B", "C"), null, null)));
        assertTrue(svc.validate(fields, Map.of()).containsKey("tags"));                         // vazio obrigatorio
        assertTrue(svc.validate(fields, Map.of("tags", List.of("A", "X"))).containsKey("tags")); // opcao invalida
        assertFalse(svc.validate(fields, Map.of("tags", List.of("A", "C"))).containsKey("tags"));
    }

    @Test
    void campo_oculto_por_condicao_nao_e_exigido() {
        FieldCondition vw = new FieldCondition("tem_obs", "==", "1");
        List<FormField> fields = svc.normalize(List.of(
                field("tem_obs", "checkbox", false, null, null, null),
                field("obs", "text", true, null, null, vw)));
        // checkbox desmarcado -> obs oculto -> nao exige
        assertFalse(svc.validate(fields, Map.of()).containsKey("obs"));
        // checkbox marcado -> obs visivel e obrigatorio
        assertTrue(svc.validate(fields, Map.of("tem_obs", true)).containsKey("obs"));
        assertFalse(svc.validate(fields, Map.of("tem_obs", true, "obs", "texto")).containsKey("obs"));
    }

    @Test
    void checkbox_obrigatorio_precisa_ser_marcado() {
        List<FormField> fields = svc.normalize(List.of(
                field("aceite", "checkbox", true, null, null, null)));
        assertTrue(svc.validate(fields, Map.of("aceite", false)).containsKey("aceite"));
        assertFalse(svc.validate(fields, Map.of("aceite", true)).containsKey("aceite"));
    }

    @Test
    void segmentacao_por_papel_separa_solicitante_e_fluxo() {
        List<FormField> fields = svc.normalize(List.of(
                roleField("descricao", "text", true, null),       // solicitante
                roleField("parecer", "text", true, "manager"),    // preenchido pelo gestor no fluxo
                roleField("visto", "text", true, "admin")));       // preenchido pelo admin no fluxo

        List<FormField> req = svc.requesterFields(fields);
        assertEquals(1, req.size());
        assertEquals("descricao", req.get(0).key());

        // no envio so valida o solicitante -> parecer/visto nao sao exigidos
        assertTrue(svc.validate(req, Map.of("descricao", "ok")).isEmpty());

        // campos pendentes do gestor (ainda vazios)
        List<FormField> pend = svc.pendingFor(fields, "manager", Map.of("descricao", "ok"));
        assertEquals(1, pend.size());
        assertEquals("parecer", pend.get(0).key());

        // apos preenchido, nao fica mais pendente
        assertTrue(svc.pendingFor(fields, "manager", Map.of("descricao", "ok", "parecer", "aprovado")).isEmpty());
    }

    @Test
    void fill_role_sem_etapa_correspondente_e_detectado_como_orfao() {
        List<FormField> fields = svc.normalize(List.of(
                roleField("descricao", "text", true, null),
                roleField("parecer", "text", true, "manager"),
                roleField("visto", "text", true, "admin")));

        assertEquals(java.util.Set.of("manager", "admin"), svc.fillRoles(fields));

        // fluxo cobre "manager" mas nao "admin" -> "visto" fica orfao
        FormField orphan = svc.firstUncoveredFillRole(fields, java.util.Set.of("manager"));
        assertNotNull(orphan);
        assertEquals("visto", orphan.key());
        assertEquals("admin", orphan.fillRole());

        // fluxo cobre ambos -> nenhum orfao
        assertNull(svc.firstUncoveredFillRole(fields, java.util.Set.of("manager", "admin")));

        // formulario sem fillRole -> nunca ha orfao, mesmo sem papeis no fluxo
        List<FormField> semPapel = svc.normalize(List.of(roleField("descricao", "text", true, null)));
        assertTrue(svc.fillRoles(semPapel).isEmpty());
        assertNull(svc.firstUncoveredFillRole(semPapel, java.util.Set.of()));
    }

    @Test
    void campos_de_anexo_exigem_obrigatoriedade_e_sao_filtrados() {
        List<FormField> fields = svc.normalize(List.of(
                field("file", "Contrato", true, null),       // anexo único obrigatório
                field("multifile", "Fotos", true, null),     // anexo múltiplo obrigatório
                field("text", "Observação", false, null)));

        // vazios -> obrigatoriedade por tipo
        Map<String, String> errors = svc.validate(fields, Map.of());
        assertEquals("Anexo obrigatório.", errors.get(fields.get(0).key()));
        assertEquals("Anexe ao menos um arquivo.", errors.get(fields.get(1).key()));

        // preenchidos com referencias -> sem erro
        Map<String, Object> data = Map.of(
                fields.get(0).key(), Map.of("id", 1, "name", "contrato.pdf"),
                fields.get(1).key(), List.of(Map.of("id", 2, "name", "a.png"), Map.of("id", 3, "name", "b.png")));
        assertTrue(svc.validate(fields, data).isEmpty());

        // fileFields filtra apenas os anexos
        List<FormField> files = svc.fileFields(fields);
        assertEquals(2, files.size());
        assertTrue(files.stream().allMatch(f -> FieldTypes.isAnyFile(f.type())));
    }

    @Test
    void validacao_de_anexo_preserva_accept_e_tamanho() {
        FormField f = svc.normalize(List.of(field("doc", "file", false, null,
                new Validation(null, null, null, null, null, null, null, null, null, null, null, null, ".pdf,.png", 5.0), null)))
                .get(0);
        assertNotNull(f.validation());
        assertEquals(".pdf,.png", f.validation().accept());
        assertEquals(5.0, f.validation().maxSizeMb());
    }

    @Test
    void round_trip_de_serializacao_preserva_campos() {
        String json = svc.writeFields(List.of(
                field("nome", "text", true, null,
                        new Validation(null, null, null, null, null, null, 2, 50, null, null, null, null, null, null), null)));
        assertTrue(json.contains("\"fields\""));
        List<FormField> back = svc.readFields(json);
        assertEquals(1, back.size());
        assertEquals("nome", back.get(0).key());
        assertNotNull(back.get(0).validation());
        assertEquals(2, back.get(0).validation().minLength());
    }
}
