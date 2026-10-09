package app.harmonis.workflow;

import app.harmonis.forms.Form;
import app.harmonis.forms.FormRepository;
import app.harmonis.forms.FormSchemaService;
import app.harmonis.forms.FormSubmission;
import app.harmonis.forms.FormSubmissionRepository;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.workflow.ProcessDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ProcessService {

    private final ProcessInstanceRepository instances;
    private final ProcessTaskRepository tasks;
    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final UserRepository users;
    private final FormSchemaService schema;
    private final ApprovalService approvals;
    private final ObjectMapper mapper;

    public ProcessService(ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                          FormRepository forms, FormSubmissionRepository submissions,
                          UserRepository users, FormSchemaService schema,
                          ApprovalService approvals, ObjectMapper mapper) {
        this.instances = instances;
        this.tasks = tasks;
        this.forms = forms;
        this.submissions = submissions;
        this.users = users;
        this.schema = schema;
        this.approvals = approvals;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<InboxItem> inbox(User user) {
        Map<Long, String> formNames = formNames();
        Map<Long, String> userNames = userNames();
        return approvals.pendingApprovalsFor(user).stream()
                .map(t -> {
                    ProcessInstance inst = t.getInstance();
                    return new InboxItem(
                            t.getId(), inst.getId(),
                            formNames.getOrDefault(inst.getFormId(), "Formulário"),
                            t.getStepName(),
                            inst.getCreatedBy() == null ? null : userNames.get(inst.getCreatedBy()),
                            inst.getCreatedAt());
                })
                .sorted(Comparator.comparing(InboxItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MineItem> mine(Long userId) {
        Map<Long, String> formNames = formNames();
        return instances.findByCreatedByOrderByCreatedAtDesc(userId).stream()
                .map(inst -> new MineItem(
                        inst.getId(),
                        formNames.getOrDefault(inst.getFormId(), "Formulário"),
                        inst.getStatus(),
                        currentStepName(inst),
                        inst.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessDetail detail(long instanceId, User user) {
        ProcessInstance inst = instances.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado."));
        Form form = forms.findById(inst.getFormId()).orElse(null);
        Map<Long, String> userNames = userNames();

        List<app.harmonis.forms.FormField> fields = form == null ? List.of()
                : schema.readFields(form.getSchemaJson());
        Map<String, Object> data = submissionData(inst.getSubmissionId());

        List<ProcessTask> all = tasks.findByInstanceIdOrderByStepIndexAscCreatedAtAsc(inst.getId());
        List<TaskView> timeline = all.stream().map(t -> new TaskView(
                t.getId(), t.getStepIndex(), t.getStepName(), t.getApproverType(), t.getRole(),
                t.getAssignedUserId() == null ? null : userNames.get(t.getAssignedUserId()),
                t.getStatus(),
                t.getDecidedBy() == null ? null : userNames.get(t.getDecidedBy()),
                t.getComment(), t.getDecidedAt())).toList();

        Long actionable = all.stream()
                .filter(t -> approvals.canDecide(t, user))
                .map(ProcessTask::getId)
                .findFirst().orElse(null);

        return new ProcessDetail(
                inst.getId(),
                form == null ? "Formulário" : form.getName(),
                inst.getStatus(),
                inst.getCreatedBy() == null ? null : userNames.get(inst.getCreatedBy()),
                inst.getCreatedAt(),
                fields, data, timeline, actionable);
    }

    @Transactional
    public void decide(long taskId, boolean approved, String comment, User user) {
        ProcessTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada."));
        if (!approvals.canDecide(task, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode decidir esta tarefa.");
        }
        approvals.decide(task, approved, comment, user.getId());
    }

    // ----- helpers -----

    private String currentStepName(ProcessInstance inst) {
        if (!inst.isPending()) return null;
        return tasks.findByInstanceIdAndStepIndex(inst.getId(), inst.getCurrentStep()).stream()
                .map(ProcessTask::getStepName).findFirst().orElse(null);
    }

    private Map<Long, String> formNames() {
        Map<Long, String> map = new HashMap<>();
        forms.findAll().forEach(f -> map.put(f.getId(), f.getName()));
        return map;
    }

    private Map<Long, String> userNames() {
        Map<Long, String> map = new HashMap<>();
        users.findAll().forEach(u -> map.put(u.getId(), u.getName()));
        return map;
    }

    private Map<String, Object> submissionData(Long submissionId) {
        FormSubmission sub = submissions.findById(submissionId).orElse(null);
        if (sub == null || sub.getDataJson() == null || sub.getDataJson().isBlank()) return Map.of();
        try { return mapper.readValue(sub.getDataJson(), new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }
}
