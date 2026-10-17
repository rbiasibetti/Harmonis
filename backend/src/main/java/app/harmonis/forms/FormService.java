package app.harmonis.forms;

import app.harmonis.forms.FormDtos.*;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.notifications.NotificationRepository;
import app.harmonis.integrations.HookBindingService;
import app.harmonis.integrations.WebhookEvents;
import app.harmonis.tenancy.TenantContext;
import app.harmonis.workflow.ApprovalService;
import app.harmonis.workflow.ProcessInstanceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
public class FormService {

    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final UserRepository users;
    private final FormSchemaService schema;
    private final ApprovalService approvals;
    private final FormAttachmentService attachmentsService;
    private final ProcessInstanceRepository instances;
    private final NotificationRepository notifications;
    private final HookBindingService hookBindings;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public FormService(FormRepository forms, FormSubmissionRepository submissions,
                       UserRepository users, FormSchemaService schema,
                       @Lazy ApprovalService approvals, FormAttachmentService attachmentsService,
                       ProcessInstanceRepository instances, NotificationRepository notifications,
                       HookBindingService hookBindings, ApplicationEventPublisher events,
                       ObjectMapper mapper) {
        this.forms = forms;
        this.submissions = submissions;
        this.users = users;
        this.schema = schema;
        this.approvals = approvals;
        this.attachmentsService = attachmentsService;
        this.instances = instances;
        this.notifications = notifications;
        this.hookBindings = hookBindings;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<FormSummary> list() {
        return forms.findAllByOrderByNameAsc().stream().map(this::toSummary).toList();
    }

    /** Lista para um usuario: quem nao gerencia formularios so ve os vinculados ao seu idioma. */
    @Transactional(readOnly = true)
    public List<FormSummary> list(String language, boolean seeAll) {
        return forms.findAllByOrderByNameAsc().stream()
                .filter(f -> seeAll || availableIn(f, language))
                .map(this::toSummary).toList();
    }

    /** 404 se o formulario nao esta vinculado ao idioma do usuario (exceto quem gerencia formularios). */
    @Transactional(readOnly = true)
    public void assertAccessible(long id, String language, boolean seeAll) {
        Form f = require(id);
        if (!seeAll && !availableIn(f, language)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado.");
        }
    }

    private static boolean availableIn(Form f, String language) {
        return app.harmonis.identity.Languages.parse(f.getLanguages())
                .contains(app.harmonis.identity.Languages.normalize(language));
    }

    private static String languagesOrDefault(List<String> requested, String current) {
        if (requested == null) return current;
        String joined = app.harmonis.identity.Languages.join(requested);
        if (joined == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecione ao menos um idioma válido para o formulário.");
        }
        return joined;
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
        form.setLanguages(languagesOrDefault(req.languages(), "pt-BR,en,es"));
        form.setSchemaJson(schema.writeFields(req.fields()));
        form.setCreatedBy(userId);
        forms.save(form);
        return toDetail(form);
    }

    @Transactional
    public FormDetail update(long id, SaveFormRequest req) {
        Form form = require(id);
        // Se ja existe um fluxo, o novo schema nao pode introduzir um "Preenchido por"
        // cujo papel nenhuma etapa do fluxo aprove (campo ficaria orfao).
        List<Map<String, Object>> flow = readFlow(form.getApprovalFlow());
        if (!flow.isEmpty()) {
            validateFillRolesCovered(req.fields(), flow);
        }
        form.setName(req.name().trim());
        form.setDescription(blankToNull(req.description()));
        form.setStatus(normalizeStatus(req.status()));
        form.setLanguages(languagesOrDefault(req.languages(), form.getLanguages()));
        form.setSchemaJson(schema.writeFields(req.fields()));
        forms.save(form);
        return toDetail(form);
    }

    @Transactional
    public void delete(long id) {
        Form form = require(id);
        // Apaga os arquivos fisicos dos anexos antes de remover o formulario
        // (o cascade do banco removeria apenas os registros).
        attachmentsService.deleteAllForForm(form.getId());
        // Processos de aprovacao referenciam o formulario e as respostas sem ON DELETE CASCADE:
        // remove, em ordem, notificacoes -> instancias (tarefas caem em cascata) -> formulario
        // (respostas caem em cascata). Tudo na mesma transacao: falhou, nada e apagado.
        notifications.deleteByProcessFormId(form.getId());
        instances.deleteByFormId(form.getId());
        forms.delete(form);
    }

    @Transactional
    public long submit(long id, Map<String, Object> data, Long userId) {
        Form form = require(id);
        if (!form.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Formulário não está publicado.");
        }
        // No envio, valida apenas os campos do solicitante; os campos atribuidos a
        // papeis sao preenchidos pelos aprovadores durante o fluxo.
        List<FormField> fields = schema.requesterFields(schema.readFields(form.getSchemaJson()));
        Map<String, String> errors = schema.validate(fields, data);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        FormSubmission sub = new FormSubmission();
        sub.setFormId(form.getId());
        sub.setSubmittedBy(userId);
        sub.setDataJson(writeData(data));
        submissions.save(sub);

        // Confirma os anexos do solicitante: vincula a submissao e reescreve os valores
        // com metadados autoritativos (do banco).
        List<FormField> fileFields = schema.fileFields(fields);
        if (!fileFields.isEmpty()) {
            Map<String, Object> committed = attachmentsService.commit(form, sub.getId(), data, fileFields, userId);
            sub.setDataJson(writeData(committed));
            submissions.save(sub);
        }

        // Webhooks "ao enviar": publicado ANTES de iniciar o processo para preservar a ordem
        // dos eventos (um processo sem etapas aplicaveis ja nasce aprovado). So dispara apos o commit.
        events.publishEvent(new WebhookEvents.Trigger(TenantContext.require(), form.getId(),
                WebhookEvents.SUBMITTED, sub.getId(), null, Instant.now()));

        // Se o formulario tem fluxo de aprovacao, inicia o processo.
        String flow = form.getApprovalFlow();
        if (flow != null && !flow.isBlank() && !flow.trim().equals("[]")) {
            approvals.start(form, sub, userId);
        }
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
        List<FormField> fields = schema.readFields(form.getSchemaJson()).stream()
                .filter(f -> !FieldTypes.isLayout(f.type()))   // blocos de layout nao viram coluna
                .toList();
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

    // ----- metricas para o dashboard -----

    public long countPublished() {
        return forms.countByStatus("published");
    }

    public record RecentSubmission(String formName, String submittedBy, java.time.Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<RecentSubmission> recentSubmissions() {
        Map<Long, String> formNames = new HashMap<>();
        forms.findAll().forEach(f -> formNames.put(f.getId(), f.getName()));
        Map<Long, String> names = new HashMap<>();
        users.findAll().forEach(u -> names.put(u.getId(), u.getName()));
        return submissions.findTop5ByOrderByCreatedAtDesc().stream()
                .map(s -> new RecentSubmission(
                        formNames.getOrDefault(s.getFormId(), "Formulário"),
                        s.getSubmittedBy() == null ? null : names.get(s.getSubmittedBy()),
                        s.getCreatedAt()))
                .toList();
    }

    // ----- fluxo de aprovacao -----

    @Transactional(readOnly = true)
    public FlowView getFlow(long id) {
        Form form = require(id);
        return new FlowView(readFlow(form.getApprovalFlow()), schema.readFields(form.getSchemaJson()),
                hookBindings.refsFor(form.getId()));
    }

    @Transactional
    public FlowView saveFlow(long id, List<Map<String, Object>> nodes, Map<String, List<Long>> hooks) {
        Form form = require(id);
        List<FormField> fields = schema.readFields(form.getSchemaJson());
        Set<String> fieldKeys = new HashSet<>();
        fields.forEach(f -> fieldKeys.add(f.key()));
        List<Map<String, Object>> safeNodes = nodes == null ? List.of() : nodes;
        validateNodes(safeNodes, fieldKeys);
        validateFillRolesCovered(fields, safeNodes);
        try {
            form.setApprovalFlow(mapper.writeValueAsString(nodes == null ? List.of() : nodes));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fluxo inválido.");
        }
        forms.save(form);
        if (hooks != null) {
            hookBindings.replace(form.getId(), hooks);
        }
        return new FlowView(readFlow(form.getApprovalFlow()), schema.readFields(form.getSchemaJson()),
                hookBindings.refsFor(form.getId()));
    }

    @SuppressWarnings("unchecked")
    private void validateNodes(List<Map<String, Object>> nodes, Set<String> fieldKeys) {
        for (Map<String, Object> node : nodes) {
            boolean gateway = "gateway".equals(node.get("type"));
            String name = Objects.toString(node.get("name"), "").trim();
            if (name.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        gateway ? "Toda decisão precisa de um nome." : "Toda etapa precisa de um nome.");
            }
            Map<String, Object> cond = (node.get("condition") instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
            if (gateway) {
                String field = cond == null ? "" : Objects.toString(cond.get("field"), "");
                if (field.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A decisão \"" + name + "\" precisa de um campo de condição.");
                }
                if (!fieldKeys.contains(field)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de condição inexistente: " + field);
                }
                validateNodes(asNodeList(node.get("then")), fieldKeys);
                validateNodes(asNodeList(node.get("else")), fieldKeys);
            } else {
                if (cond != null) {
                    String field = Objects.toString(cond.get("field"), "");
                    if (!field.isBlank() && !fieldKeys.contains(field)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de condição inexistente: " + field);
                    }
                }
                List<Map<String, Object>> approvers = asNodeList(node.get("approvers"));
                boolean legacy = node.get("approver_type") != null || node.get("role") != null || node.get("user_id") != null;
                if (approvers.isEmpty() && !legacy) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A etapa \"" + name + "\" precisa de ao menos um aprovador.");
                }
            }
        }
    }

    /**
     * Garante que todo papel usado em "Preenchido por" (fillRole) seja aprovador de ao menos
     * uma etapa do fluxo. Sem isso, os campos complementares daquele papel nunca seriam
     * apresentados durante o processo (ficariam orfaos).
     */
    private void validateFillRolesCovered(List<FormField> fields, List<Map<String, Object>> nodes) {
        if (schema.fillRoles(fields).isEmpty()) return;
        Set<String> flowRoles = new HashSet<>();
        collectFlowRoles(nodes, flowRoles);
        FormField orphan = schema.firstUncoveredFillRole(fields, flowRoles);
        if (orphan != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O campo \"" + orphan.label() + "\" é preenchido pelo papel \"" + orphan.fillRole().trim()
                    + "\", mas nenhuma etapa do fluxo é aprovada por esse papel. "
                    + "Adicione uma etapa com esse papel ou altere o \"Preenchido por\" do campo.");
        }
    }

    /** Coleta os papeis de todos os aprovadores do tipo "role" em toda a arvore do fluxo. */
    @SuppressWarnings("unchecked")
    private void collectFlowRoles(List<Map<String, Object>> nodes, Set<String> out) {
        for (Map<String, Object> node : nodes) {
            if ("gateway".equals(node.get("type"))) {
                collectFlowRoles(asNodeList(node.get("then")), out);
                collectFlowRoles(asNodeList(node.get("else")), out);
                continue;
            }
            List<Map<String, Object>> approvers = asNodeList(node.get("approvers"));
            if (approvers.isEmpty()) {
                // legado: approver_type/role no topo da etapa
                addRole(node.get("approver_type"), node.get("role"), out);
            } else {
                for (Map<String, Object> a : approvers) {
                    addRole(a.get("approver_type"), a.get("role"), out);
                }
            }
        }
    }

    private static void addRole(Object approverType, Object role, Set<String> out) {
        String type = approverType == null ? "role" : approverType.toString();
        if (!"user".equals(type)) {
            String r = Objects.toString(role, "").trim();
            if (!r.isEmpty()) out.add(r);
        }
    }

    private List<Map<String, Object>> readFlow(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asNodeList(Object v) {
        return (v instanceof List<?>) ? (List<Map<String, Object>>) v : List.of();
    }

    // ----- helpers -----

    private Form require(long id) {
        return forms.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado."));
    }

    private FormSummary toSummary(Form f) {
        return new FormSummary(f.getId(), f.getName(), f.getSlug(), f.getDescription(),
                f.getStatus(), schema.readFields(f.getSchemaJson()).size(),
                submissions.countByFormId(f.getId()),
                app.harmonis.identity.Languages.parse(f.getLanguages()));
    }

    private FormDetail toDetail(Form f) {
        return new FormDetail(f.getId(), f.getName(), f.getSlug(), f.getDescription(),
                f.getStatus(), schema.readFields(f.getSchemaJson()),
                app.harmonis.identity.Languages.parse(f.getLanguages()));
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
        if (FieldTypes.isAnyFile(f.type())) return attachmentNames(value);
        if (value instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) { if (sb.length() > 0) sb.append(", "); sb.append(o); }
            return sb.toString();
        }
        return value.toString();
    }

    /** Nome(s) de arquivo a partir de uma referencia de anexo (objeto ou lista de objetos). */
    @SuppressWarnings("unchecked")
    private static String attachmentNames(Object value) {
        if (value instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) { String n = oneName(o); if (!n.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(n); } }
            return sb.toString();
        }
        return oneName(value);
    }

    private static String oneName(Object o) {
        if (o instanceof Map<?, ?> m && m.get("name") != null) return m.get("name").toString();
        return o == null ? "" : o.toString();
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
