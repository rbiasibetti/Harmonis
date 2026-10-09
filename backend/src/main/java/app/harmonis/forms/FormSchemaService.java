package app.harmonis.forms;

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

/** Serializacao do schema e validacao dinamica de respostas (porta de FormSchema). */
@Service
public class FormSchemaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALNUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ObjectMapper mapper;

    public FormSchemaService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Le os campos do schema persistido (JSON {"fields":[...]}). */
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

    /** Normaliza e serializa os campos recebidos no builder para persistir. */
    public String writeFields(List<FormField> input) {
        List<FormField> normalized = normalize(input);
        try {
            return mapper.writeValueAsString(Map.of("fields", normalized));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schema inválido.");
        }
    }

    /** Valida tipos e coisas basicas de cada campo; gera id/key quando faltarem. */
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
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo campo precisa de um rótulo.");
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
            out.add(new FormField(id, key, type, f.label().trim(),
                    f.placeholder(), f.help(), f.required(), options));
        }
        return out;
    }

    /** Valida os dados enviados contra os campos. Retorna mapa de erros (key -> mensagem). */
    public Map<String, String> validate(List<FormField> fields, Map<String, Object> data) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> safe = data == null ? Map.of() : data;

        for (FormField f : fields) {
            Object value = safe.get(f.key());

            if ("checkbox".equals(f.type())) {
                boolean checked = toBool(value);
                if (f.required() && !checked) {
                    errors.put(f.key(), "Confirmação obrigatória.");
                }
                continue;
            }

            boolean empty = value == null || value.toString().isBlank();
            if (empty) {
                if (f.required()) errors.put(f.key(), "Campo obrigatório.");
                continue;
            }

            String s = value.toString().trim();
            switch (f.type()) {
                case "number" -> {
                    try { Double.parseDouble(s); }
                    catch (NumberFormatException e) { errors.put(f.key(), "Informe um número válido."); }
                }
                case "email" -> {
                    if (!EMAIL.matcher(s).matches()) errors.put(f.key(), "Informe um e-mail válido.");
                }
                case "date" -> {
                    try { LocalDate.parse(s); }
                    catch (DateTimeParseException e) { errors.put(f.key(), "Informe uma data válida (AAAA-MM-DD)."); }
                }
                case "select", "radio" -> {
                    if (!f.optionsOrEmpty().contains(s)) errors.put(f.key(), "Opção inválida.");
                }
                default -> { /* text/textarea: qualquer string */ }
            }
        }
        return errors;
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = v.toString().trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("on") || s.equals("sim");
    }

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
