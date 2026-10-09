package app.harmonis.workflow;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FlowTest {

    private Map<String, Object> cond(String field, String op, String value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field); c.put("operator", op); c.put("value", value);
        return c;
    }

    private Map<String, Object> step(String id, String name) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id); s.put("name", name);
        List<Map<String, Object>> aps = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("approver_type", "role"); a.put("role", "manager");
        aps.add(a);
        s.put("approvers", aps);
        return s;
    }

    private Map<String, Object> gateway(String id, String name, Map<String, Object> condition,
                                        List<Map<String, Object>> then, List<Map<String, Object>> els) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id); g.put("type", "gateway"); g.put("name", name);
        g.put("condition", condition); g.put("then", then); g.put("else", els);
        return g;
    }

    // ---- conditionPasses ----

    @Test
    void condicao_nula_ou_sem_campo_passa() {
        assertTrue(Flow.conditionPasses(null, Map.of()));
        assertTrue(Flow.conditionPasses(cond("", "==", "x"), Map.of("a", "b")));
    }

    @Test
    void comparacao_numerica() {
        Map<String, Object> data = Map.of("valor", "1200");
        assertTrue(Flow.conditionPasses(cond("valor", ">", "1000"), data));
        assertFalse(Flow.conditionPasses(cond("valor", ">", "2000"), data));
        assertTrue(Flow.conditionPasses(cond("valor", ">=", "1200"), data));
        assertTrue(Flow.conditionPasses(cond("valor", "<", "1300"), data));
    }

    @Test
    void comparacao_relacional_com_nao_numerico_e_falsa() {
        assertFalse(Flow.conditionPasses(cond("txt", ">", "10"), Map.of("txt", "abc")));
    }

    @Test
    void igualdade_frouxa_numerica_e_textual() {
        assertTrue(Flow.conditionPasses(cond("v", "==", "1200"), Map.of("v", "1200.0")));
        assertTrue(Flow.conditionPasses(cond("uf", "==", "sp"), Map.of("uf", "SP")));
        assertTrue(Flow.conditionPasses(cond("uf", "!=", "RJ"), Map.of("uf", "SP")));
    }

    @Test
    void booleano_vira_1_ou_0() {
        assertTrue(Flow.conditionPasses(cond("ok", "==", "1"), Map.of("ok", true)));
        assertTrue(Flow.conditionPasses(cond("ok", "==", "0"), Map.of("ok", false)));
    }

    @Test
    void empty_e_not_empty() {
        assertTrue(Flow.conditionPasses(cond("obs", "empty", ""), Map.of()));
        assertTrue(Flow.conditionPasses(cond("obs", "empty", ""), Map.of("obs", "")));
        assertTrue(Flow.conditionPasses(cond("obs", "not_empty", ""), Map.of("obs", "texto")));
        assertFalse(Flow.conditionPasses(cond("obs", "not_empty", ""), Map.of("obs", "")));
    }

    @Test
    void contains_case_insensitive() {
        assertTrue(Flow.conditionPasses(cond("desc", "contains", "urgente"), Map.of("desc", "Pedido URGENTE")));
        assertFalse(Flow.conditionPasses(cond("desc", "contains", "xpto"), Map.of("desc", "Pedido")));
    }

    // ---- normalizeStep ----

    @Test
    void normaliza_legado_e_modo() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("name", "Aprovação");
        legacy.put("approver_type", "role");
        legacy.put("role", "manager");
        Map<String, Object> norm = Flow.normalizeStep(legacy);
        assertEquals(1, Flow.approvers(norm).size());
        assertEquals("all", norm.get("mode"));

        Map<String, Object> bad = step("s", "S");
        bad.put("mode", "qualquer");
        assertEquals("all", Flow.normalizeStep(bad).get("mode"));

        Map<String, Object> any = step("s", "S");
        any.put("mode", "any");
        assertEquals("any", Flow.normalizeStep(any).get("mode"));
    }

    // ---- resolvePlan ----

    @Test
    void plano_simples_mantem_ordem() {
        List<Map<String, Object>> nodes = List.of(step("a", "A"), step("b", "B"));
        List<Map<String, Object>> plan = Flow.resolvePlan(nodes, Map.of());
        assertEquals(List.of("A", "B"), plan.stream().map(n -> n.get("name")).toList());
    }

    @Test
    void gateway_segue_ramo_then_quando_verdadeiro() {
        Map<String, Object> gw = gateway("g", "Alto?",
                cond("valor", ">", "1000"),
                List.of(step("d", "Diretoria")),
                List.of(step("m", "Gestor")));
        List<Map<String, Object>> plan = Flow.resolvePlan(List.of(gw), Map.of("valor", "5000"));
        assertEquals(List.of("Diretoria"), plan.stream().map(n -> n.get("name")).toList());
    }

    @Test
    void gateway_segue_ramo_else_quando_falso() {
        Map<String, Object> gw = gateway("g", "Alto?",
                cond("valor", ">", "1000"),
                List.of(step("d", "Diretoria")),
                List.of(step("m", "Gestor")));
        List<Map<String, Object>> plan = Flow.resolvePlan(List.of(gw), Map.of("valor", "500"));
        assertEquals(List.of("Gestor"), plan.stream().map(n -> n.get("name")).toList());
    }

    @Test
    void gateway_aninhado_e_achatado() {
        Map<String, Object> inner = gateway("g2", "Muito alto?",
                cond("valor", ">", "10000"),
                List.of(step("ceo", "CEO")),
                List.of(step("dir", "Diretoria")));
        Map<String, Object> outer = gateway("g1", "Alto?",
                cond("valor", ">", "1000"),
                List.of(inner),
                List.of(step("m", "Gestor")));
        List<Map<String, Object>> nodes = List.of(step("ini", "Triagem"), outer);

        List<Map<String, Object>> plan = Flow.resolvePlan(nodes, Map.of("valor", "50000"));
        assertEquals(List.of("Triagem", "CEO"), plan.stream().map(n -> n.get("name")).toList());

        List<Map<String, Object>> plan2 = Flow.resolvePlan(nodes, Map.of("valor", "5000"));
        assertEquals(List.of("Triagem", "Diretoria"), plan2.stream().map(n -> n.get("name")).toList());
    }
}
