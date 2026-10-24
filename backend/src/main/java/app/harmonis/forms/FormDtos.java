package app.harmonis.forms;

import app.harmonis.integrations.IntegrationDtos.HookRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FormDtos {

    private FormDtos() {}

    public record FormSummary(
            Long id, String name, String slug, String description,
            String status, int fields, long submissions, List<String> languages
    ) {}

    public record FormDetail(
            Long id, String name, String slug, String description,
            String status, List<FormField> fields, List<String> languages
    ) {}

    public record SaveFormRequest(
            @NotBlank String name,
            String description,
            String status,
            @NotNull List<FormField> fields,
            /** Idiomas vinculados (>= 1). Nulo: todos na criacao / mantem na edicao. */
            List<String> languages
    ) {}

    public record SubmitRequest(@NotNull Map<String, Object> data) {}

    public record SubmissionView(
            Long id, String submittedBy, Instant createdAt, Map<String, Object> data
    ) {}

    /** Fluxo de aprovacao + campos do formulario (para o editor de condicoes). */
    public record FlowView(List<Map<String, Object>> nodes, List<FormField> fields,
                           Map<String, List<HookRef>> hooks) {}

    /** hooks: evento -> ids de integracoes; nulo = nao altera os vinculos atuais. */
    public record SaveFlowRequest(@NotNull List<Map<String, Object>> nodes, Map<String, List<Long>> hooks) {}

    /** Referencia de um anexo (retornada no upload e guardada na resposta). */
    public record AttachmentRef(Long id, String name, long size, String mime) {}

    /** Referencia de uma assinatura recem-capturada (vai no valor do campo). */
    public record SignatureRef(
            Long signatureId, String style, String signedAt,
            SignerRef signer, String ip, String device, GeoRef geo) {}

    public record SignerRef(Long id, String name, String email) {}

    public record GeoRef(String status, Double lat, Double lon, Double accuracy) {}
}
