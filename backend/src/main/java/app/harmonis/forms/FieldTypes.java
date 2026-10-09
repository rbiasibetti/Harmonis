package app.harmonis.forms;

import java.util.List;
import java.util.Set;

/** Catalogo dos 8 tipos de campo (porta de config/forms.php). */
public final class FieldTypes {

    private FieldTypes() {}

    public record TypeInfo(String code, String label, boolean hasOptions) {}

    public static final List<TypeInfo> ALL = List.of(
            new TypeInfo("text", "Texto curto", false),
            new TypeInfo("textarea", "Texto longo", false),
            new TypeInfo("number", "Número", false),
            new TypeInfo("email", "E-mail", false),
            new TypeInfo("date", "Data", false),
            new TypeInfo("select", "Lista suspensa", true),
            new TypeInfo("radio", "Múltipla escolha", true),
            new TypeInfo("checkbox", "Confirmação (sim/não)", false)
    );

    private static final Set<String> CODES =
            Set.of("text", "textarea", "number", "email", "date", "select", "radio", "checkbox");

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }

    public static boolean hasOptions(String code) {
        return "select".equals(code) || "radio".equals(code);
    }
}
