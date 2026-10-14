package app.harmonis.workflow;

import app.harmonis.forms.Form;
import app.harmonis.forms.FormAttachmentService;
import app.harmonis.forms.FormField;
import app.harmonis.forms.FormRepository;
import app.harmonis.forms.FormSchemaService;
import app.harmonis.forms.FormService;
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
    private final FormAttachmentService attachmentsService;
    private final ObjectMapper mapper;

    public ProcessService(ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                          FormRepository forms, FormSubmissionRepository submissions,
                          UserRepository users, FormSchemaService schema,
                          ApprovalService approvals, FormAttachmentService attachmentsService,
                          ObjectMapper mapper) {
        this.instances = instances;
        this.tasks = tasks;
        this.forms = forms;
        this.submissions = submissions;
        this.users = users;
        this.schema = schema;
        this.approvals = approvals;
        this.attachmentsService = attachmentsService;
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

        List<FormField> fields = form == null ? List.of()
                : schema.readFields(form.getSchemaJson());
        Map<String, Object> data = submissionData(inst.getSubmissionId());

        List<ProcessTask> all = tasks.findByInstanceIdOrderByStepIndexAscCreatedAtAsc(inst.getId());
        List<TaskView> timeline = all.stream().map(t -> new TaskView(
                t.getId(), t.getStepIndex(), t.getStepName(), t.getApproverType(), t.getRole(),
                t.getAssignedUserId() == null ? null : userNames.get(t.getAssignedUserId()),
                t.getStatus(),
                t.getDecidedBy() == null ? null : userNames.get(t.getDecidedBy()),
                t.getComment(), t.getDecidedAt())).toList();

        ProcessTask actionableTask = all.stream()
                .filter(t -> approvals.canDecide(t, user))
                .findFirst().orElse(null);
        Long actionable = actionableTask == null ? null : actionableTask.getId();

        // Campos complementares que este aprovador (pelo seu papel) precisa preencher.
        List<FormField> fillFields = List.of();
        if (actionableTask != null && "role".equals(actionableTask.getApproverType()) && actionableTask.getRole() != null) {
            fillFields = schema.pendingFor(fields, actionableTask.getRole(), data);
        }

        return new ProcessDetail(
                inst.getId(),
                inst.getFormId(),
                form == null ? "Formulário" : form.getName(),
                inst.getStatus(),
                inst.getCreatedBy() == null ? null : userNames.get(inst.getCreatedBy()),
                inst.getCreatedAt(),
                fields, data, timeline, actionable, fillFields);
    }

    @Transactional
    public void decide(long taskId, boolean approved, String comment, Map<String, Object> fieldValues, User user) {
        ProcessTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada."));
        if (!approvals.canDecide(task, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode decidir esta tarefa.");
        }
        // Ao aprovar, mescla e valida os campos complementares atribuidos ao papel desta tarefa.
        if (approved) {
            mergeComplementaryFields(task, fieldValues, user.getId());
        }
        approvals.decide(task, approved, comment, user.getId());
    }

    private void mergeComplementaryFields(ProcessTask task, Map<String, Object> fieldValues, Long userId) {
        if (!"role".equals(task.getApproverType()) || task.getRole() == null) return;
        ProcessInstance inst = task.getInstance();
        Form form = forms.findById(inst.getFormId()).orElse(null);
        if (form == null) return;

        List<FormField> all = schema.readFields(form.getSchemaJson());
        Map<String, Object> data = new HashMap<>(submissionData(inst.getSubmissionId()));
        List<FormField> assigned = schema.pendingFor(all, task.getRole(), data);
        if (assigned.isEmpty()) return;

        Set<String> keys = new HashSet<>();
        assigned.forEach(f -> keys.add(f.key()));
        if (fieldValues != null) {
            fieldValues.forEach((k, v) -> { if (keys.contains(k)) data.put(k, v); });
        }

        Map<String, String> errors = schema.validate(assigned, data);
        if (!errors.isEmpty()) {
            throw new FormService.ValidationException(errors);
        }

        // Confirma anexos dos campos de arquivo preenchidos por este papel no fluxo.
        List<FormField> fileFields = schema.fileFields(assigned);
        if (!fileFields.isEmpty()) {
            attachmentsService.commit(form, inst.getSubmissionId(), data, fileFields, userId);
        }

        FormSubmission sub = submissions.findById(inst.getSubmissionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submissão não encontrada."));
        try {
            sub.setDataJson(mapper.writeValueAsString(data));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        }
        submissions.save(sub);
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
