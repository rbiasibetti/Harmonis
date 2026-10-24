package app.harmonis.datamodels;

import java.util.ArrayList;
import java.util.List;

/** Leitor de CSV simples: detecta ',' ou ';', trata aspas duplas e quebras de linha em campos. */
final class Csv {

    private Csv() {}

    static List<List<String>> parse(String text) {
        if (text == null || text.isBlank()) return List.of();
        char delim = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delim) {
                cur.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                cur.add(field.toString()); field.setLength(0);
                rows.add(cur); cur = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !cur.isEmpty()) { cur.add(field.toString()); rows.add(cur); }
        return rows;
    }

    private static char detectDelimiter(String text) {
        int nl = text.indexOf('\n');
        String firstLine = nl < 0 ? text : text.substring(0, nl);
        long commas = firstLine.chars().filter(ch -> ch == ',').count();
        long semis = firstLine.chars().filter(ch -> ch == ';').count();
        return semis > commas ? ';' : ',';
    }
}
