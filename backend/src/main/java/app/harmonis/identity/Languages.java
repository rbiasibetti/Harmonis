package app.harmonis.identity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Idiomas suportados pela aplicacao e utilitarios de normalizacao. */
public final class Languages {

    private Languages() {}

    public static final String DEFAULT = "pt-BR";
    public static final List<String> SUPPORTED = List.of("pt-BR", "en", "es");

    /** Normaliza um codigo (ex.: "pt", "en-US", "ES") para um idioma suportado, ou o padrao. */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) return DEFAULT;
        String c = code.trim().toLowerCase();
        if (c.startsWith("pt")) return "pt-BR";
        if (c.startsWith("en")) return "en";
        if (c.startsWith("es")) return "es";
        return DEFAULT;
    }

    /** Codigo estritamente valido (ou null) — para entrada do usuario. */
    public static String requireSupported(String code) {
        return code != null && SUPPORTED.contains(code.trim()) ? code.trim() : null;
    }

    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>(SUPPORTED);
        Set<String> out = new LinkedHashSet<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (SUPPORTED.contains(t)) out.add(t);
        }
        return new ArrayList<>(out);
    }

    /** Valida e junta uma lista de idiomas (ordem canonica, sem repeticao). Vazia/invalida -> null. */
    public static String join(List<String> langs) {
        if (langs == null || langs.isEmpty()) return null;
        List<String> ordered = new ArrayList<>();
        for (String s : SUPPORTED) if (langs.contains(s)) ordered.add(s);
        if (ordered.size() != new LinkedHashSet<>(langs).size()) return null; // algum idioma desconhecido
        return String.join(",", ordered);
    }
}
