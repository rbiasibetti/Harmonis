package app.harmonis.forms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FormDtos {

    private FormDtos() {}

    public record FormSummary(
            Long id, String name, String slug, String description,
            String status, int fields, long submissions
    ) {}

    public record FormDetail(
            Long id, String name, String slug, String description,
            String status, List<FormField> fields
    ) {}

    public record SaveFormRequest(
            @NotBlank String name,
            String description,
            String status,
            @NotNull List<FormField> fields
    ) {}

    public record SubmitRequest(@NotNull Map<String, Object> data) {}

    public record SubmissionView(
            Long id, String submittedBy, Instant createdAt, Map<String, Object> data
    ) {}

    /** Fluxo de aprovacao + campos do formulario (para o editor de condicoes). */
    public record FlowView(List<Map<String, Object>> nodes, List<FormField> fields) {}

    public record SaveFlowRequest(@NotNull List<Map<String, Object>> nodes) {}

    /** Referencia de um anexo (retornada no upload e guardada na resposta). */
    public record AttachmentRef(Long id, String name, long size, String mime) {}
}
