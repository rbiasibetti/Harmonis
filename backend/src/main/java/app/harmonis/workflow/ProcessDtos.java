package app.harmonis.workflow;

import app.harmonis.forms.FormField;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ProcessDtos {

    private ProcessDtos() {}

    public record InboxItem(
            Long taskId, Long instanceId, String formName, String stepName,
            String requestedBy, Instant createdAt
    ) {}

    public record MineItem(
            Long instanceId, String formName, String status, String currentStep, Instant createdAt
    ) {}

    public record TaskView(
            Long id, int stepIndex, String stepName, String approverType, String role,
            String assignedUser, String status, String decidedBy, String comment, Instant decidedAt
    ) {}

    public record ProcessDetail(
            Long instanceId, Long formId, String formName, String status, String requestedBy, Instant createdAt,
            List<FormField> fields, Map<String, Object> data, List<TaskView> timeline, Long actionableTaskId,
            List<FormField> fillFields
    ) {}

    public record DecideRequest(@NotNull Boolean approved, String comment, Map<String, Object> fieldValues) {}
}
