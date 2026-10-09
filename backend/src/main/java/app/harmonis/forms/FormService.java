package app.harmonis.forms;

import app.harmonis.forms.FormDtos.*;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;

@Service
public class FormService {

    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final UserRepository users;
    private final FormSchemaService schema;
    private final ObjectMapper mapper;

    public FormService(FormRepository forms, FormSubmissionRepository submissions,
                       UserRepository users, FormSchemaService schema, ObjectMapper mapper) {
        this.forms = forms;
        this.submissions = submissions;
        this.users = users;
        this.schema = schema;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<FormSummary> list() {
        return forms.findAllByOrderByNameAsc().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public FormDetail get(long id) {
        return toDetail(require(id));
    }

    @Transactional
    public FormDetail create(SaveFormRequest req, Long userId) {
        Form form = new Form();
        form.setName(req.name().trim());
        form.setSlug(uniqueSlug(slugify(req.name())));
        form.setDescription(blankToNull(req.description()));
        form.setStatus(normalizeStatus(req.status()));
        form.setSchemaJson(schema.writeFields(req.fields()));
        form.setCreatedBy(userId);
        forms.save(form);
        return toDetail(form);
    }

    @Transactional
    public FormDetail update(long id, SaveFormRequest req) {
        Form form = require(id);
        form.setName(req.name().trim());
        form.setDescription(blankToNull(req.description()));
        form.setStatus(normalizeStatus(req.status()));
        form.setSchemaJson(schema.writeFields(req.fields()));
        forms.save(form);
        return toDetail(form);
    }

    @Transactional
    public void delete(long id) {
        forms.delete(require(id));
    }

    @Transactional
    public long submit(long id, Map<String, Object> data, Long userId) {
        Form form = require(id);
        if (!form.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Formulário não está publicado.");
        }
        List<FormField> fields = schema.readFields(form.getSchemaJson());
        Map<String, String> errors = schema.validate(fields, data);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        FormSubmission sub = new FormSubmission();
        sub.setFormId(form.getId());
        sub.setSubmittedBy(userId);
        sub.setDataJson(writeData(data));
        submissions.save(sub);
        return sub.getId();
    }

    @Transactional(readOnly = true)
    public List<SubmissionView> submissions(long formId) {
        require(formId);
        Map<Long, String> names = userNames();
        return submissions.findByFormIdOrderByCreatedAtDesc(formId).stream()
                .map(s -> new SubmissionView(
                        s.getId(),
                        s.getSubmittedBy() == null ? null : names.get(s.getSubmittedBy()),
                        s.getCreatedAt(),
                        readData(s.getDataJson())))
                .toList();
    }

    /** CSV pt-BR: separador ';' + BOM UTF-8; checkbox -> Sim/Nao. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(long formId) {
        Form form = require(formId);
        List<FormField> fields = schema.readFields(form.getSchemaJson());
        Map<Long, String> names = userNames();
        List<FormSubmission> rows = submissions.findByFormIdOrderByCreatedAtDesc(form.getId());

        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM
        List<String> header = new ArrayList<>(List.of("#", "Enviado por", "Data"));
        fields.forEach(f -> header.add(f.label()));
        sb.append(csvLine(header));

        int i = rows.size();
        for (FormSubmission s : rows) {
            Map<String, Object> data = readData(s.getDataJson());
            List<String> cols = new ArrayList<>();
            cols.add(String.valueOf(i--));
            cols.add(s.getSubmittedBy() == null ? "—" : names.getOrDefault(s.getSubmittedBy(), "—"));
            cols.add(s.getCreatedAt() == null ? "" : s.getCreatedAt().toString());
            for (FormField f : fields) cols.add(formatValue(f, data.get(f.key())));
            sb.append(csvLine(cols));
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public String formName(long formId) {
        return require(formId).getName();
    }

    // ----- helpers -----

    private Form require(long id) {
        return forms.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado."));
    }

    private FormSummary toSummary(Form f) {
        return new FormSummary(f.getId(), f.getName(), f.getSlug(), f.getDescription(),
                f.getStatus(), schema.readFields(f.getSchemaJson()).size(),
                submissions.countByFormId(f.getId()));
    }

    private FormDetail toDetail(Form f) {
        return new FormDetail(f.getId(), f.getName(), f.getSlug(), f.getDescription(),
                f.getStatus(), schema.readFields(f.getSchemaJson()));
    }

    private Map<Long, String> userNames() {
        Map<Long, String> map = new HashMap<>();
        for (User u : users.findAll()) map.put(u.getId(), u.getName());
        return map;
    }

    private static String formatValue(FormField f, Object value) {
        if (value == null) return "";
        if ("checkbox".equals(f.type())) {
            boolean b = (value instanceof Boolean bb) ? bb
                    : List.of("true", "1", "on", "sim").contains(value.toString().toLowerCase());
            return b ? "Sim" : "Não";
        }
        if (value instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) { if (sb.length() > 0) sb.append(", "); sb.append(o); }
            return sb.toString();
        }
        return value.toString();
    }

    private static String csvLine(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(csvCell(cols.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    private static String csvCell(String v) {
        if (v == null) v = "";
        boolean needsQuote = v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        v = v.replace("\"", "\"\"");
        return needsQuote ? "\"" + v + "\"" : v;
    }

    private String writeData(Map<String, Object> data) {
        try { return mapper.writeValueAsString(data == null ? Map.of() : data); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados inválidos."); }
    }

    private Map<String, Object> readData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String normalizeStatus(String status) {
        return "published".equals(status) ? "published" : "draft";
    }

    private String uniqueSlug(String base) {
        String slug = base.isBlank() ? "form" : base;
        String candidate = slug;
        int n = 2;
        while (forms.existsBySlug(candidate)) candidate = slug + "-" + n++;
        return candidate;
    }

    private static String slugify(String raw) {
        String noAccents = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Erros de validacao de uma submissao (422). */
    public static class ValidationException extends RuntimeException {
        private final transient Map<String, String> errors;
        public ValidationException(Map<String, String> errors) { this.errors = errors; }
        public Map<String, String> getErrors() { return errors; }
    }
}
