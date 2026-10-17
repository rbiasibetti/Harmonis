package app.harmonis.forms;

import app.harmonis.forms.FormField.FieldCondition;
import app.harmonis.forms.FormField.Validation;
import app.harmonis.workflow.Flow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Serializacao do schema e validacao dinamica de respostas (porta estendida de FormSchema). */
@Service
public class FormSchemaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALNUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> WIDTHS = Set.of("full", "half", "third");

    private final ObjectMapper mapper;

    public FormSchemaService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<FormField> readFields(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return List.of();
        try {
            Map<String, Object> root = mapper.readValue(schemaJson, new TypeReference<>() {});
            Object fields = root.get("fields");
            if (fields == null) return List.of();
            return mapper.convertValue(fields, new TypeReference<List<FormField>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public String writeFields(List<FormField> input) {
        List<FormField> normalized = normalize(input);
        try {
            return mapper.writeValueAsString(Map.of("fields", normalized));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schema inválido.");
        }
    }

    public List<FormField> normalize(List<FormField> input) {
        if (input == null) return List.of();
        Set<String> usedKeys = new HashSet<>();
        List<FormField> out = new ArrayList<>();
        for (FormField f : input) {
            String type = f.type();
            if (!FieldTypes.isValid(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de campo inválido: " + type);
            }
            if (f.label() == null || f.label().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        FieldTypes.isLayout(type) ? "Todo bloco precisa de um título." : "Todo campo precisa de um rótulo.");
            }
            String width = (f.width() != null && WIDTHS.contains(f.width())) ? f.width() : "full";

            if (FieldTypes.isLayout(type)) {
                // bloco/secao: sem key, sem opcoes, sem validacao
                out.add(new FormField("s_" + randomToken(8), null, type, f.label().trim(),
                        null, f.help(), false, null, null, width, null, null, null));
                continue;
            }

            String key = (f.key() == null || f.key().isBlank()) ? uniqueKey(usedKeys) : f.key();
            while (!usedKeys.add(key)) key = uniqueKey(usedKeys);
            String id = (f.id() == null || f.id().isBlank()) ? "f_" + randomToken(8) : f.id();

            List<String> options = FieldTypes.hasOptions(type)
                    ? f.optionsOrEmpty().stream().filter(o -> o != null && !o.isBlank()).toList()
                    : null;
            if (FieldTypes.hasOptions(type) && (options == null || options.isEmpty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O campo \"" + f.label() + "\" precisa de ao menos uma opção.");
            }

            String display = normalizeDisplay(type, f.display());
            Validation validation = sanitizeValidation(f.validation());
            FieldCondition visibleWhen = sanitizeCondition(f.visibleWhen());

            out.add(new FormField(id, key, type, f.label().trim(),
                    f.placeholder(), f.help(), f.required(), options,
                    display, width, validation, visibleWhen, nullIfBlank(f.fillRole())));
        }
        return out;
    }

    // ----- validacao de respostas -----

    public Map<String, String> validate(List<FormField> fields, Map<String, Object> data) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> safe = data == null ? Map.of() : data;

        for (FormField f : fields) {
            if (FieldTypes.isLayout(f.type())) continue;
            if (!visible(f, safe)) continue; // campo oculto: nao valida nem exige

            Object value = safe.get(f.key());

            if ("checkbox".equals(f.type())) {
                if (f.required() && !toBool(value)) errors.put(f.key(), "Confirmação obrigatória.");
                continue;
            }

            if (FieldTypes.isMultiChoice(f.type())) {
                List<String> chosen = toList(value);
                if (f.required() && chosen.isEmpty()) { errors.put(f.key(), "Selecione ao menos uma opção."); continue; }
                for (String v : chosen) {
                    if (!f.optionsOrEmpty().contains(v)) { errors.put(f.key(), "Opção inválida."); break; }
                }
                continue;
            }

            if (FieldTypes.isAnyFile(f.type())) {
                // O valor sao referencias de anexo (um objeto, ou lista deles); tipo/tamanho
                // sao validados no upload. Aqui so checamos obrigatoriedade.
                boolean emptyFile = FieldTypes.isMultiFile(f.type())
                        ? toList(value).isEmpty()
                        : isEmptyValue(value);
                if (f.required() && emptyFile) {
                    errors.put(f.key(), FieldTypes.isMultiFile(f.type()) ? "Anexe ao menos um arquivo." : "Anexo obrigatório.");
                }
                continue;
            }

            boolean empty = value == null || value.toString().isBlank();
            if (empty) {
                if (f.required()) errors.put(f.key(), "Campo obrigatório.");
                continue;
            }

            String s = value.toString().trim();
            Validation v = f.validation();

            switch (f.type()) {
                case "number" -> validateNumber(f, s, v, safe, errors);
                case "email" -> {
                    if (!EMAIL.matcher(s).matches()) errors.put(f.key(), "Informe um e-mail válido.");
                    else applyPattern(f, s, v, errors);
                }
                case "date" -> validateDate(f, s, v, safe, errors);
                case "select", "radio" -> {
                    if (!f.optionsOrEmpty().contains(s)) errors.put(f.key(), "Opção inválida.");
                }
                default -> { // text / textarea
                    applyLength(f, s, v, errors);
                    if (!errors.containsKey(f.key())) applyPattern(f, s, v, errors);
                }
            }
        }
        return errors;
    }

    // ----- segmentacao por papel (preenchimento durante o fluxo) -----

    /** Campos preenchidos pelo solicitante no envio (sem fillRole). */
    public List<FormField> requesterFields(List<FormField> fields) {
        return fields.stream().filter(f -> blank(f.fillRole())).toList();
    }

    /** Subconjunto de campos de anexo (file/multifile). */
    public List<FormField> fileFields(List<FormField> fields) {
        return fields.stream().filter(f -> FieldTypes.isAnyFile(f.type())).toList();
    }

    /** Papeis distintos usados em algum campo com "Preenchido por" (preserva a ordem de aparicao). */
    public java.util.LinkedHashSet<String> fillRoles(List<FormField> fields) {
        java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
        for (FormField f : fields) {
            if (!blank(f.fillRole())) roles.add(f.fillRole().trim());
        }
        return roles;
    }

    /**
     * Primeiro campo cujo "Preenchido por" (fillRole) nao esta coberto por nenhum papel do fluxo
     * (coverRoles), ou null se todos estiverem cobertos. Campos sem fillRole sao ignorados.
     */
    public FormField firstUncoveredFillRole(List<FormField> fields, java.util.Set<String> coverRoles) {
        java.util.Set<String> cover = coverRoles == null ? java.util.Set.of() : coverRoles;
        for (FormField f : fields) {
            if (blank(f.fillRole())) continue;
            if (!cover.contains(f.fillRole().trim())) return f;
        }
        return null;
    }

    /** Campos atribuidos a um papel, ainda vazios e visiveis sob os dados atuais. */
    public List<FormField> pendingFor(List<FormField> fields, String role, Map<String, Object> data) {
        if (role == null || role.isBlank()) return List.of();
        Map<String, Object> safe = data == null ? Map.of() : data;
        return fields.stream()
                .filter(f -> role.equals(f.fillRole()))
                .filter(f -> isEmptyValue(safe.get(f.key())))
                .filter(f -> visible(f, safe))
                .toList();
    }

    private static boolean isEmptyValue(Object v) {
        if (v == null) return true;
        if (v instanceof List<?> l) return l.isEmpty();
        return v.toString().isBlank();
    }

    private void validateNumber(FormField f, String s, Validation v, Map<String, Object> data, Map<String, String> errors) {
        double num;
        try { num = Double.parseDouble(s); }
        catch (NumberFormatException e) { errors.put(f.key(), "Informe um número válido."); return; }

        if (v != null) {
            if (Boolean.TRUE.equals(v.positive()) && num <= 0) { errors.put(f.key(), "Informe um valor positivo."); return; }
            if (Boolean.TRUE.equals(v.integer()) && num != Math.floor(num)) { errors.put(f.key(), "Informe um número inteiro."); return; }
            if (v.min() != null && num < v.min()) { errors.put(f.key(), "Valor mínimo: " + trimNum(v.min())); return; }
            if (v.max() != null && num > v.max()) { errors.put(f.key(), "Valor máximo: " + trimNum(v.max())); return; }
            if (!crossCompareNumber(f, num, v.afterField(), data, true, errors)) return;
            if (!crossCompareNumber(f, num, v.beforeField(), data, false, errors)) return;
        }
    }

    private void validateDate(FormField f, String s, Validation v, Map<String, Object> data, Map<String, String> errors) {
        LocalDate date;
        try { date = LocalDate.parse(s); }
        catch (DateTimeParseException e) { errors.put(f.key(), "Informe uma data válida (AAAA-MM-DD)."); return; }

        if (v != null) {
            if (v.minDate() != null && !v.minDate().isBlank()) {
                LocalDate min = parseDate(v.minDate());
                if (min != null && date.isBefore(min)) { errors.put(f.key(), "Data mínima: " + min); return; }
            }
            if (v.maxDate() != null && !v.maxDate().isBlank()) {
                LocalDate max = parseDate(v.maxDate());
                if (max != null && date.isAfter(max)) { errors.put(f.key(), "Data máxima: " + max); return; }
            }
            if (!crossCompareDate(f, date, v.afterField(), data, true, errors)) return;
            if (!crossCompareDate(f, date, v.beforeField(), data, false, errors)) return;
        }
    }

    /** after=true exige this>other; after=false exige this<other. Retorna false se gerou erro. */
    private boolean crossCompareNumber(FormField f, double value, String otherKey, Map<String, Object> data,
                                       boolean after, Map<String, String> errors) {
        if (otherKey == null || otherKey.isBlank()) return true;
        Object o = data.get(otherKey);
        if (o == null || o.toString().isBlank()) return true;
        Double other = toNumber(o.toString());
        if (other == null) return true;
        if (after && value <= other) { errors.put(f.key(), "Deve ser maior que o campo referenciado."); return false; }
        if (!after && value >= other) { errors.put(f.key(), "Deve ser menor que o campo referenciado."); return false; }
        return true;
    }

    private boolean crossCompareDate(FormField f, LocalDate value, String otherKey, Map<String, Object> data,
                                     boolean after, Map<String, String> errors) {
        if (otherKey == null || otherKey.isBlank()) return true;
        Object o = data.get(otherKey);
        if (o == null || o.toString().isBlank()) return true;
        LocalDate other = parseDate(o.toString().trim());
        if (other == null) return true;
        if (after && !value.isAfter(other)) { errors.put(f.key(), "Deve ser posterior ao campo referenciado."); return false; }
        if (!after && !value.isBefore(other)) { errors.put(f.key(), "Deve ser anterior ao campo referenciado."); return false; }
        return true;
    }

    private void applyPattern(FormField f, String s, Validation v, Map<String, String> errors) {
        if (v == null || v.pattern() == null || v.pattern().isBlank()) return;
        try {
            if (!Pattern.compile(v.pattern()).matcher(s).matches()) {
                errors.put(f.key(), (v.patternMessage() != null && !v.patternMessage().isBlank())
                        ? v.patternMessage() : "Formato inválido.");
            }
        } catch (PatternSyntaxException ignored) {
            // regex invalida no schema: ignora (nao bloqueia o preenchimento)
        }
    }

    private void applyLength(FormField f, String s, Validation v, Map<String, String> errors) {
        if (v == null) return;
        if (v.minLength() != null && s.length() < v.minLength()) errors.put(f.key(), "Mínimo de " + v.minLength() + " caracteres.");
        else if (v.maxLength() != null && s.length() > v.maxLength()) errors.put(f.key(), "Máximo de " + v.maxLength() + " caracteres.");
    }

    /** O campo esta visivel/habilitado para preenchimento? (visibleWhen). */
    public boolean visible(FormField f, Map<String, Object> data) {
        FieldCondition c = f.visibleWhen();
        if (c == null || c.field() == null || c.field().isBlank()) return true;
        Map<String, Object> cond = new HashMap<>();
        cond.put("field", c.field());
        cond.put("operator", c.operator() == null ? "not_empty" : c.operator());
        cond.put("value", c.value());
        return Flow.conditionPasses(cond, data);
    }

    // ----- helpers de normalizacao -----

    private static String normalizeDisplay(String type, String display) {
        if (FieldTypes.isSingleChoice(type)) {
            if ("radio".equals(type)) return "radio";
            return "radio".equals(display) ? "radio" : "combo";
        }
        if (FieldTypes.isMultiChoice(type)) {
            return "checkbox".equals(display) ? "checkbox" : "combo";
        }
        return null;
    }

    private static Validation sanitizeValidation(Validation v) {
        if (v == null) return null;
        // descarta regex invalida
        String pattern = v.pattern();
        if (pattern != null && !pattern.isBlank()) {
            try { Pattern.compile(pattern); } catch (PatternSyntaxException e) { pattern = null; }
        } else {
            pattern = null;
        }
        Double maxSizeMb = (v.maxSizeMb() != null && v.maxSizeMb() > 0) ? v.maxSizeMb() : null;
        boolean empty = pattern == null && v.min() == null && v.max() == null
                && !Boolean.TRUE.equals(v.positive()) && !Boolean.TRUE.equals(v.integer())
                && v.minLength() == null && v.maxLength() == null
                && blank(v.minDate()) && blank(v.maxDate())
                && blank(v.afterField()) && blank(v.beforeField())
                && blank(v.accept()) && maxSizeMb == null;
        if (empty) return null;
        return new Validation(pattern, v.patternMessage(), v.min(), v.max(),
                v.positive(), v.integer(), v.minLength(), v.maxLength(),
                nullIfBlank(v.minDate()), nullIfBlank(v.maxDate()),
                nullIfBlank(v.afterField()), nullIfBlank(v.beforeField()),
                nullIfBlank(v.accept()), maxSizeMb);
    }

    private static FieldCondition sanitizeCondition(FieldCondition c) {
        if (c == null || c.field() == null || c.field().isBlank()) return null;
        return new FieldCondition(c.field(), c.operator() == null ? "not_empty" : c.operator(), c.value());
    }

    // ----- utils -----

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = v.toString().trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("on") || s.equals("sim");
    }

    @SuppressWarnings("unchecked")
    private static List<String> toList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> l) return l.stream().filter(Objects::nonNull).map(Object::toString).toList();
        String s = v.toString().trim();
        return s.isEmpty() ? List.of() : List.of(s);
    }

    private static Double toNumber(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private static String trimNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String nullIfBlank(String s) { return blank(s) ? null : s.trim(); }

    private static String uniqueKey(Set<String> used) {
        String key;
        do { key = "campo_" + randomToken(6); } while (used.contains(key));
        return key;
    }

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        return sb.toString();
    }
}
