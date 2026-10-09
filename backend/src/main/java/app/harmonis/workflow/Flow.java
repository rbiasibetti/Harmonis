package app.harmonis.workflow;

import java.util.*;

/**
 * Motor de fluxo (porta 1:1 de app/Workflow/Flow.php).
 *
 * O {@code approval_flow} e uma arvore de nos (etapa ou gateway). {@link #resolvePlan}
 * achata a arvore num plano linear de etapas, resolvendo cada gateway contra os dados
 * (imutaveis) da submissao — determinístico, com indices estaveis.
 */
public final class Flow {

    private Flow() {}

    public static final List<String> OPERATORS =
            List.of("==", "!=", ">", ">=", "<", "<=", "contains", "empty", "not_empty");

    public static boolean isGateway(Map<String, Object> node) {
        return node != null && "gateway".equals(node.get("type"));
    }

    /** Garante approvers[] + mode valido (compatibiliza no legado approver_type/role/user_id no topo). */
    public static Map<String, Object> normalizeStep(Map<String, Object> node) {
        Map<String, Object> step = new LinkedHashMap<>(node);
        Object approvers = step.get("approvers");
        if (!(approvers instanceof List<?> list) || list.isEmpty()) {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("approver_type", step.getOrDefault("approver_type", "role"));
            single.put("role", step.get("role"));
            single.put("user_id", step.get("user_id"));
            List<Map<String, Object>> built = new ArrayList<>();
            built.add(single);
            step.put("approvers", built);
        }
        String mode = Objects.toString(step.get("mode"), "all");
        step.put("mode", (mode.equals("all") || mode.equals("any")) ? mode : "all");
        return step;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> approvers(Map<String, Object> step) {
        Object a = step.get("approvers");
        return (a instanceof List<?>) ? (List<Map<String, Object>>) a : List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> condition(Map<String, Object> node) {
        Object c = node.get("condition");
        return (c instanceof Map<?, ?>) ? (Map<String, Object>) c : null;
    }

    /** Avalia uma condicao contra os dados da submissao. */
    public static boolean conditionPasses(Map<String, Object> condition, Map<String, Object> data) {
        if (condition == null) return true;
        String field = Objects.toString(condition.get("field"), "");
        if (field.isBlank()) return true;

        String operator = Objects.toString(condition.get("operator"), "==");
        String expected = Objects.toString(condition.get("value"), "");

        Object actualObj = data == null ? null : data.get(field);
        if (actualObj instanceof Boolean b) actualObj = b ? "1" : "0";

        if (operator.equals("empty")) return isEmpty(actualObj);
        if (operator.equals("not_empty")) return !isEmpty(actualObj);

        String actual = actualObj == null ? "" : actualObj.toString();
        return switch (operator) {
            case "==" -> looseEquals(actual, expected);
            case "!=" -> !looseEquals(actual, expected);
            case ">", ">=", "<", "<=" -> numericCompare(actual, expected, operator);
            case "contains" -> actual.toLowerCase().contains(expected.toLowerCase());
            default -> false;
        };
    }

    /** Achata a arvore num plano linear de etapas, resolvendo gateways contra os dados. */
    public static List<Map<String, Object>> resolvePlan(List<Map<String, Object>> nodes, Map<String, Object> data) {
        List<Map<String, Object>> plan = new ArrayList<>();
        if (nodes == null) return plan;
        for (Map<String, Object> node : nodes) {
            if (isGateway(node)) {
                boolean pass = conditionPasses(condition(node), data);
                List<Map<String, Object>> branch = asNodes(node.get(pass ? "then" : "else"));
                plan.addAll(resolvePlan(branch, data));
            } else {
                plan.add(node);
            }
        }
        return plan;
    }

    // ----- helpers -----

    private static boolean isEmpty(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isEmpty();
        if (v instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private static boolean looseEquals(String a, String b) {
        Double na = toNumber(a), nb = toNumber(b);
        if (na != null && nb != null) return na.doubleValue() == nb.doubleValue();
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean numericCompare(String a, String b, String op) {
        Double na = toNumber(a), nb = toNumber(b);
        if (na == null || nb == null) return false;
        int c = Double.compare(na, nb);
        return switch (op) {
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            default -> false;
        };
    }

    private static Double toNumber(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asNodes(Object v) {
        return (v instanceof List<?>) ? (List<Map<String, Object>>) v : List.of();
    }
}
