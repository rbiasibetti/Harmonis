package app.harmonis.integrations;

import app.harmonis.forms.*;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.workflow.ProcessInstance;
import app.harmonis.workflow.ProcessInstanceRepository;
import app.harmonis.workflow.ProcessTask;
import app.harmonis.workflow.ProcessTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Monta o corpo JSON enviado aos webhooks a partir do estado ja confirmado no banco. */
@Service
public class WebhookPayloadBuilder {

    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final ProcessInstanceRepository instances;
    private final ProcessTaskRepository tasks;
    private final UserRepository users;
    private final FormSchemaService schema;
    private final ObjectMapper mapper;

    public WebhookPayloadBuilder(FormRepository forms, FormSubmissionRepository submissions,
                                 ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                                 UserRepository users, FormSchemaService schema, ObjectMapper mapper) {
        this.forms = forms;
        this.submissions = submissions;
        this.instances = instances;
        this.tasks = tasks;
        this.users = users;
        this.schema = schema;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(WebhookEvents.Trigger t) {
        Form form = forms.findById(t.formId()).orElseThrow(() -> new IllegalStateException("Formulário removido."));
        FormSubmission sub = t.submissionId() == null ? null : submissions.findById(t.submissionId()).orElse(null);
        ProcessInstance instance = t.instanceId() != null
                ? instances.findById(t.instanceId()).orElse(null)
                : (t.submissionId() == null ? null : instances.findFirstBySubmissionId(t.submissionId()).orElse(null));

        Map<String, Object> data = sub == null ? Map.of() : readData(sub.getDataJson());
        List<ProcessTask> taskList = instance == null ? List.of()
                : tasks.findByInstanceIdOrderByStepIndexAscCreatedAtAsc(instance.getId());

        Set<Long> userIds = new HashSet<>();
        if (sub != null && sub.getSubmittedBy() != null) userIds.add(sub.getSubmittedBy());
        taskList.forEach(k -> {
            if (k.getDecidedBy() != null) userIds.add(k.getDecidedBy());
            if (k.getAssignedUserId() != null) userIds.add(k.getAssignedUserId());
        });
        Map<Long, User> byId = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("event", t.event());
        root.put("occurredAt", t.occurredAt().toString());
        root.put("tenant", t.tenant());
        root.put("form", Map.of("id", form.getId(), "name", form.getName(), "slug", form.getSlug()));

        if (sub != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", sub.getId());
            s.put("submittedAt", sub.getCreatedAt() == null ? null : sub.getCreatedAt().toString());
            s.put("submittedBy", person(byId.get(sub.getSubmittedBy())));
            s.put("data", data);
            s.put("answers", answers(schema.readFields(form.getSchemaJson()), data));
            root.put("submission", s);
        }

        if (instance != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", instance.getId());
            p.put("status", instance.getStatus());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (ProcessTask k : taskList) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("step", k.getStepName());
                step.put("stepIndex", k.getStepIndex());
                step.put("approver", "user".equals(k.getApproverType())
                        ? Optional.ofNullable(byId.get(k.getAssignedUserId())).map(User::getName).orElse("usuário")
                        : "papel: " + k.getRole());
                step.put("status", k.getStatus());
                step.put("decidedBy", person(byId.get(k.getDecidedBy())));
                step.put("comment", k.getComment());
                step.put("decidedAt", k.getDecidedAt() == null ? null : k.getDecidedAt().toString());
                steps.add(step);
            }
            p.put("steps", steps);
            root.put("process", p);
        }
        return root;
    }

    /** Corpo de exemplo do botao "Testar". */
    public Map<String, Object> sample(String tenant) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("event", WebhookEvents.TEST);
        root.put("occurredAt", Instant.now().toString());
        root.put("tenant", tenant);
        root.put("form", Map.of("id", 0, "name", "Formulário de exemplo", "slug", "formulario-de-exemplo"));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", 0);
        s.put("submittedAt", Instant.now().toString());
        s.put("submittedBy", Map.of("id", 0, "name", "Usuário de teste", "email", "teste@exemplo.com"));
        s.put("data", Map.of("campo_exemplo", "valor de teste"));
        s.put("answers", List.of(Map.of("key", "campo_exemplo", "label", "Campo de exemplo",
                "type", "text", "value", "valor de teste")));
        root.put("submission", s);
        root.put("process", Map.of("id", 0, "status", "approved", "steps", List.of()));
        return root;
    }

    private List<Map<String, Object>> answers(List<FormField> fields, Map<String, Object> data) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FormField f : fields) {
            if (f.key() == null || "section".equals(f.type())) continue;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("key", f.key());
            a.put("label", f.label());
            a.put("type", f.type());
            a.put("value", data.get(f.key()));
            out.add(a);
        }
        return out;
    }

    private static Map<String, Object> person(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        return m;
    }

    private Map<String, Object> readData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }
}
