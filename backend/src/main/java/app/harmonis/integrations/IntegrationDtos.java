package app.harmonis.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class IntegrationDtos {

    private IntegrationDtos() {}

    public record NameValue(String name, String value) {}

    /**
     * Configuracao de autenticacao (entrada e formato persistido). Em segredos
     * (password, keyValue, token), nulo/vazio na ATUALIZACAO significa "manter o atual".
     * type: none | basic | apikey | bearer | bearer_preauth | headers.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthSpec(
            String type,
            // basic / pre-autenticacao
            String username, String password,
            // apikey
            String keyName, String keyValue, String keyIn,
            // bearer estatico
            String token, String tokenPrefix,
            // bearer com pre-autenticacao
            String tokenUrl, String tokenBodyFormat,
            String usernameField, String passwordField, List<NameValue> tokenExtra,
            String tokenPath, String expiresInPath, Integer tokenTtlSeconds
    ) {
        public static AuthSpec none() {
            return new AuthSpec("none", null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    /** Autenticacao devolvida ao cliente: segredos nunca saem; so o indicador "definido". */
    public record AuthView(
            String type, String username, boolean passwordSet,
            String keyName, boolean keyValueSet, String keyIn,
            boolean tokenSet, String tokenPrefix,
            String tokenUrl, String tokenBodyFormat,
            String usernameField, String passwordField, List<NameValue> tokenExtra,
            String tokenPath, String expiresInPath, Integer tokenTtlSeconds
    ) {}

    /** Cabecalho customizado (entrada e formato persistido). Se secret, o valor e cifrado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeaderSpec(String name, String value, boolean secret) {}

    public record HeaderView(String name, String value, boolean secret, boolean valueSet) {}

    public record SaveIntegrationRequest(
            @NotBlank String name,
            String description,
            @NotBlank String url,
            String method,
            @NotNull AuthSpec auth,
            List<HeaderSpec> headers,
            /** nulo = manter; preenchido = definir. */
            String signingSecret,
            Boolean clearSigningSecret,
            Integer timeoutSeconds,
            Integer retries
    ) {}

    public record IntegrationView(
            Long id, String name, String description, String url, String method,
            AuthView auth, List<HeaderView> headers, boolean signed,
            int timeoutSeconds, int retries, String status, Instant publishedAt,
            long usageCount, Instant updatedAt
    ) {}

    public record IntegrationSummary(
            Long id, String name, String description, String url, String method,
            String authType, String status, long usageCount,
            String lastStatus, Instant lastAt
    ) {}

    /** Integracao publicada disponivel para vincular no fluxo. */
    public record IntegrationOption(Long id, String name, String description) {}

    /** Onde uma integracao e usada. */
    public record UsageRef(Long formId, String formName, String event) {}

    /** Vinculo exibido no editor de fluxo. */
    public record HookRef(Long id, String name, boolean published) {}

    // ----- logs -----

    public record LogRow(
            Long id, String deliveryId, int attempt, Long integrationId, String integrationName,
            Long formId, String formName, Long submissionId, String event, String status,
            Integer httpStatus, Integer durationMs, String errorMessage, boolean willRetry,
            boolean manual, Instant createdAt
    ) {}

    public record LogDetail(
            Long id, String deliveryId, int attempt, Long integrationId, String integrationName,
            Long formId, String formName, Long submissionId, String event, String status,
            Integer httpStatus, Integer durationMs, String errorMessage, boolean willRetry,
            boolean manual, Instant createdAt,
            String method, String requestUrl, String requestBody, String responseBody
    ) {}

    public record LogPage(List<LogRow> items, long total, int page, int size) {}

    public record LogStats(long success24h, long error24h, long skipped24h) {}
}
