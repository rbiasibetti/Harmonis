package app.harmonis.forms;

import java.util.List;
import java.util.Set;

/** Catalogo de tipos de campo (porta estendida de config/forms.php). */
public final class FieldTypes {

    private FieldTypes() {}

    public record TypeInfo(String code, String label, boolean hasOptions, String kind) {}

    /** kind: 'input' (campo com valor) | 'single' | 'multiple' | 'file' | 'multifile' | 'layout'. */
    public static final List<TypeInfo> ALL = List.of(
            new TypeInfo("text", "Texto curto", false, "input"),
            new TypeInfo("textarea", "Texto longo", false, "input"),
            new TypeInfo("number", "Número", false, "input"),
            new TypeInfo("email", "E-mail", false, "input"),
            new TypeInfo("date", "Data", false, "input"),
            new TypeInfo("select", "Seleção única", true, "single"),
            new TypeInfo("multiselect", "Seleção múltipla", true, "multiple"),
            new TypeInfo("checkbox", "Confirmação (sim/não)", false, "input"),
            new TypeInfo("file", "Anexo (único)", false, "file"),
            new TypeInfo("multifile", "Anexo (múltiplo)", false, "multifile"),
            new TypeInfo("signature", "Assinatura", false, "signature"),
            new TypeInfo("lookup", "Consulta (modelo de dados)", false, "lookup"),
            new TypeInfo("section", "Bloco / seção", false, "layout")
    );

    // 'radio' permanece valido para compatibilidade (dados antigos); a UI usa select + display.
    private static final Set<String> CODES =
            Set.of("text", "textarea", "number", "email", "date",
                    "select", "radio", "multiselect", "checkbox", "file", "multifile", "signature", "lookup", "section");

    private static final Set<String> WITH_OPTIONS = Set.of("select", "radio", "multiselect");

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }

    public static boolean hasOptions(String code) {
        return WITH_OPTIONS.contains(code);
    }

    /** Campos de layout (sem valor): nao entram em validacao/CSV. */
    public static boolean isLayout(String code) {
        return "section".equals(code);
    }

    public static boolean isSingleChoice(String code) {
        return "select".equals(code) || "radio".equals(code);
    }

    public static boolean isMultiChoice(String code) {
        return "multiselect".equals(code);
    }

    /** Anexo único. */
    public static boolean isFile(String code) {
        return "file".equals(code);
    }

    /** Anexo múltiplo (valor = lista de referências). */
    public static boolean isMultiFile(String code) {
        return "multifile".equals(code);
    }

    /** Qualquer campo de anexo (único ou múltiplo). */
    public static boolean isAnyFile(String code) {
        return isFile(code) || isMultiFile(code);
    }

    /** Campo de assinatura (desenhada ou selfie). O valor guarda os metadados coletados. */
    public static boolean isSignature(String code) {
        return "signature".equals(code);
    }

    /** Campo de consulta a um modelo de dados (lookup). O valor guarda a(s) selecao(oes). */
    public static boolean isLookup(String code) {
        return "lookup".equals(code);
    }
}
