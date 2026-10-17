package app.harmonis.workflow;

import app.harmonis.forms.Form;
import app.harmonis.forms.FormRepository;
import app.harmonis.forms.FormSubmission;
import app.harmonis.forms.FormSubmissionRepository;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.integrations.WebhookEvents;
import app.harmonis.tenancy.TenantContext;
import app.harmonis.notifications.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Execucao do workflow de aprovacao (porta 1:1 de app/Workflow/ApprovalService.php).
 * Toda a execucao roda sobre o PLANO linear (Flow.resolvePlan), entao o executor permanece
 * linear mesmo com gateways/paralelo/condicional.
 */
@Service
public class ApprovalService {

    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final ProcessInstanceRepository instances;
    private final ProcessTaskRepository tasks;
    private final UserRepository users;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public ApprovalService(FormRepository forms, FormSubmissionRepository submissions,
                           ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                           UserRepository users, NotificationService notifications,
                           ApplicationEventPublisher events, ObjectMapper mapper) {
        this.forms = forms;
        this.submissions = submissions;
        this.instances = instances;
        this.tasks = tasks;
        this.users = users;
        this.notifications = notifications;
        this.events = events;
        this.mapper = mapper;
    }

    /** Inicia um processo para uma submissao e abre a primeira etapa aplicavel. */
    @Transactional
    public ProcessInstance start(Form form, FormSubmission submission, Long creatorId) {
        ProcessInstance instance = new ProcessInstance();
        instance.setFormId(form.getId());
        instance.setSubmissionId(submission.getId());
        instance.setStatus("pending");
        instance.setCurrentStep(0);
        instance.setCreatedBy(creatorId);
        instances.save(instance);

        openStepFrom(instance, 0);
        return instance;
    }

    /** Abre a proxima etapa aplicavel a partir de fromIndex; se nenhuma, aprova o processo. */
    @Transactional
    public void openStepFrom(ProcessInstance instance, int fromIndex) {
        Form form = forms.findById(instance.getFormId()).orElseThrow();
        Map<String, Object> data = dataOf(instance);
        List<Map<String, Object>> plan = Flow.resolvePlan(flowOf(form), data);

        for (int i = fromIndex; i < plan.size(); i++) {
            Map<String, Object> step = Flow.normalizeStep(plan.get(i));
            if (!Flow.conditionPasses(Flow.condition(step), data)) {
                continue; // etapa condicional pulada
            }
            instance.setCurrentStep(i);
            instances.save(instance);
            List<ProcessTask> created = createTasksForStep(instance, step, i);
            notifyApprovalRequested(instance, form, created);
            return;
        }

        // nenhuma etapa aplicavel -> aprovado
        instance.setStatus("approved");
        instances.save(instance);
        notifyCompleted(instance, form, true);
        fireWebhooks(instance, WebhookEvents.APPROVED);
        fireWebhooks(instance, WebhookEvents.COMPLETED);
    }

    private List<ProcessTask> createTasksForStep(ProcessInstance instance, Map<String, Object> step, int index) {
        String stepName = Objects.toString(step.get("name"), "Etapa " + (index + 1));
        List<ProcessTask> created = new ArrayList<>();
        for (Map<String, Object> approver : Flow.approvers(step)) {
            String type = Objects.toString(approver.get("approver_type"), "role");
            ProcessTask task = new ProcessTask();
            task.setInstance(instance);
            task.setStepIndex(index);
            task.setStepName(stepName);
            task.setApproverType(type);
            if ("user".equals(type)) {
                task.setAssignedUserId(toLong(approver.get("user_id")));
            } else {
                task.setRole(Objects.toString(approver.get("role"), null));
            }
            task.setStatus("pending");
            tasks.save(task);
            created.add(task);
        }
        return created;
    }

    /** Registra a decisao de uma tarefa e avanca/encerra o processo conforme o modo. */
    @Transactional
    public void decide(ProcessTask task, boolean approved, String comment, Long userId) {
        if (!task.isPending()) return;

        task.setStatus(approved ? "approved" : "rejected");
        task.setDecidedBy(userId);
        task.setComment(comment);
        task.setDecidedAt(Instant.now());
        tasks.save(task);

        ProcessInstance instance = task.getInstance();
        if (!instance.isPending()) return;

        int index = task.getStepIndex();
        String mode = modeAt(instance, index);

        if ("any".equals(mode)) {
            if (approved) {
                skipRemaining(instance, index);
                openStepFrom(instance, index + 1);
            } else {
                long pending = tasks.countByInstanceIdAndStepIndexAndStatus(instance.getId(), index, "pending");
                long okays = tasks.countByInstanceIdAndStepIndexAndStatus(instance.getId(), index, "approved");
                if (pending == 0 && okays == 0) {
                    reject(instance);
                }
            }
        } else { // all
            if (!approved) {
                skipRemaining(instance, index);
                reject(instance);
            } else {
                long pending = tasks.countByInstanceIdAndStepIndexAndStatus(instance.getId(), index, "pending");
                if (pending == 0) {
                    openStepFrom(instance, index + 1);
                }
            }
        }
    }

    private void skipRemaining(ProcessInstance instance, int index) {
        for (ProcessTask t : tasks.findByInstanceIdAndStepIndex(instance.getId(), index)) {
            if (t.isPending()) {
                t.setStatus("skipped");
                tasks.save(t);
            }
        }
    }

    private void reject(ProcessInstance instance) {
        instance.setStatus("rejected");
        instances.save(instance);
        Form form = forms.findById(instance.getFormId()).orElse(null);
        notifyCompleted(instance, form, false);
        fireWebhooks(instance, WebhookEvents.REJECTED);
        fireWebhooks(instance, WebhookEvents.COMPLETED);
    }

    /** Publica o gatilho de webhook; a entrega ocorre apos o commit, fora desta transacao. */
    private void fireWebhooks(ProcessInstance instance, String event) {
        events.publishEvent(new WebhookEvents.Trigger(TenantContext.require(), instance.getFormId(), event,
                instance.getSubmissionId(), instance.getId(), Instant.now()));
    }

    /** Pode o usuario decidir esta tarefa? */
    public boolean canDecide(ProcessTask task, User user) {
        if (!task.isPending()) return false;
        if (user.isOwner()) return true;
        if ("user".equals(task.getApproverType())) {
            return task.getAssignedUserId() != null && task.getAssignedUserId().equals(user.getId());
        }
        return task.getRole() != null && user.roleNames().contains(task.getRole());
    }

    /** Usuarios que podem decidir a tarefa (usuario especifico, ou ativos com o papel). */
    @Transactional(readOnly = true)
    public List<User> approversFor(ProcessTask task) {
        if ("user".equals(task.getApproverType())) {
            if (task.getAssignedUserId() == null) return List.of();
            return users.findById(task.getAssignedUserId()).map(List::of).orElse(List.of());
        }
        String role = task.getRole();
        if (role == null) return List.of();
        return users.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.roleNames().contains(role))
                .toList();
    }

    /** Tarefas abertas que o usuario pode decidir (caixa de aprovacao / contador). */
    @Transactional(readOnly = true)
    public List<ProcessTask> pendingApprovalsFor(User user) {
        List<ProcessTask> open = tasks.findOpenTasks();
        if (user.isOwner()) return open;
        Set<String> roles = user.roleNames();
        return open.stream()
                .filter(t -> ("user".equals(t.getApproverType())
                        ? user.getId().equals(t.getAssignedUserId())
                        : t.getRole() != null && roles.contains(t.getRole())))
                .toList();
    }

    // ----- notificacoes -----

    private void notifyApprovalRequested(ProcessInstance instance, Form form, List<ProcessTask> created) {
        Set<Long> targets = new LinkedHashSet<>();
        for (ProcessTask task : created) {
            for (User u : approversFor(task)) {
                if (!u.getId().equals(instance.getCreatedBy())) {
                    targets.add(u.getId());
                }
            }
        }
        String formName = form == null ? "Formulário" : form.getName();
        for (Long uid : targets) {
            notifications.notify(uid, "ApprovalRequested",
                    "Aprovação solicitada",
                    formName + " aguarda sua decisão.",
                    "/processes", instance.getId());
        }
    }

    private void notifyCompleted(ProcessInstance instance, Form form, boolean approved) {
        String formName = form == null ? "Formulário" : form.getName();
        notifications.notify(instance.getCreatedBy(), "ProcessCompleted",
                approved ? "Solicitação aprovada" : "Solicitação reprovada",
                formName + (approved ? " foi aprovada." : " foi reprovada."),
                "/processes", instance.getId());
    }

    // ----- helpers -----

    private String modeAt(ProcessInstance instance, int index) {
        Form form = forms.findById(instance.getFormId()).orElseThrow();
        Map<String, Object> data = dataOf(instance);
        List<Map<String, Object>> plan = Flow.resolvePlan(flowOf(form), data);
        if (index < 0 || index >= plan.size()) return "all";
        return Objects.toString(Flow.normalizeStep(plan.get(index)).get("mode"), "all");
    }

    private Map<String, Object> dataOf(ProcessInstance instance) {
        FormSubmission sub = submissions.findById(instance.getSubmissionId()).orElse(null);
        if (sub == null || sub.getDataJson() == null || sub.getDataJson().isBlank()) return Map.of();
        try { return mapper.readValue(sub.getDataJson(), new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private List<Map<String, Object>> flowOf(Form form) {
        String json = form.getApprovalFlow();
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
