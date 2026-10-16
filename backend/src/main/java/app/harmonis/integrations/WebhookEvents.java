package app.harmonis.integrations;

import java.time.Instant;
import java.util.List;

/** Eventos que podem disparar webhooks e o gatilho publicado pelos servicos. */
public final class WebhookEvents {

    private WebhookEvents() {}

    public static final String SUBMITTED = "submitted";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    /** Apenas para o botao "Testar" e reenvios manuais; nao e vinculavel a formularios. */
    public static final String TEST = "test";

    /** Eventos vinculaveis a um formulario. */
    public static final List<String> BINDABLE = List.of(SUBMITTED, APPROVED, REJECTED);

    /**
     * Gatilho de negocio. Publicado dentro da transacao e tratado depois do commit
     * ({@link WebhookDispatcher}), no contexto do tenant de origem.
     */
    public record Trigger(String tenant, long formId, String event, Long submissionId,
                          Long instanceId, Instant occurredAt) {}
}
