package app.harmonis.integrations;

import app.harmonis.forms.Form;
import app.harmonis.forms.FormRepository;
import app.harmonis.tenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Orquestra a entrega dos webhooks. Os gatilhos chegam depois do COMMIT da transacao de
 * origem (nunca disparam em rollback) e a entrega roda em threads proprias, no contexto
 * do tenant — a aprovacao nao espera nem falha por causa do sistema de destino.
 * Cada tentativa gera uma linha de log; falhas transitorias sao reagendadas.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    /** Contexto imutavel de uma entrega (compartilhado pelas tentativas). */
    record Delivery(String tenant, String deliveryId, Long integrationId, Long formId, String formName,
                    Long submissionId, String event, String body, Long triggeredBy) {}

    private final IntegrationService integrations;
    private final HookBindingService bindings;
    private final WebhookPayloadBuilder payloads;
    private final WebhookSender sender;
    private final IntegrationLogRepository logs;
    private final FormRepository forms;
    private final ObjectMapper mapper;
    private final ThreadPoolTaskScheduler scheduler;
    private final List<Integer> retryDelays;

    public WebhookDispatcher(IntegrationService integrations, HookBindingService bindings,
                             WebhookPayloadBuilder payloads, WebhookSender sender,
                             IntegrationLogRepository logs, FormRepository forms, ObjectMapper mapper,
                             @Qualifier("webhookScheduler") ThreadPoolTaskScheduler scheduler,
                             @Qualifier("webhookRetryDelays") List<Integer> retryDelays) {
        this.integrations = integrations;
        this.bindings = bindings;
        this.payloads = payloads;
        this.sender = sender;
        this.logs = logs;
        this.forms = forms;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.retryDelays = retryDelays.isEmpty() ? List.of(30) : retryDelays;
    }

    // ----- gatilhos de negocio -----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTrigger(WebhookEvents.Trigger t) {
        try {
            scheduler.execute(() -> inTenant(t.tenant(), () -> handle(t)));
        } catch (RejectedExecutionException e) {
            log.warn("Fila de webhooks indisponivel; evento {} do formulario {} descartado.", t.event(), t.formId());
        }
    }

    private void handle(WebhookEvents.Trigger t) {
        List<Long> ids = bindings.integrationIdsFor(t.formId(), t.event());
        if (ids.isEmpty()) return;

        String formName = forms.findById(t.formId()).map(Form::getName).orElse(null);
        String body;
        try {
            body = mapper.writeValueAsString(payloads.build(t));
        } catch (Exception e) {
            log.error("Falha ao montar o payload do evento {} (form {}): {}", t.event(), t.formId(), e.toString());
            for (Long id : ids) {
                Delivery d = new Delivery(t.tenant(), UUID.randomUUID().toString(), id, t.formId(), formName,
                        t.submissionId(), t.event(), null, null);
                String name = integrations.find(id).map(Integration::getName).orElse("(integração " + id + ")");
                save(d, 1, id, name, IntegrationLog.ERROR, null, null, null, null,
                        "Não foi possível montar o payload: " + e.getMessage(), 0, false, null);
            }
            return;
        }
        for (Long id : ids) {
            attempt(new Delivery(t.tenant(), UUID.randomUUID().toString(), id, t.formId(), formName,
                    t.submissionId(), t.event(), body, null), 1);
        }
    }

    // ----- entrega -----

    /** Uma tentativa de entrega: envia, grava o log e, se cabivel, agenda a proxima. Nunca lanca. */
    IntegrationLog attempt(Delivery d, int n) {
        try {
            Integration integ = integrations.find(d.integrationId()).orElse(null);
            if (integ == null) {
                return save(d, n, d.integrationId(), "(integração excluída)", IntegrationLog.SKIPPED,
                        null, null, null, null, "A integração foi excluída; entrega ignorada.", 0, false, null);
            }
            boolean manual = d.triggeredBy() != null;
            if (!integ.isPublished() && !manual) {
                return save(d, n, integ.getId(), integ.getName(), IntegrationLog.SKIPPED,
                        null, integ.getMethod(), WebhookSender.maskedUrl(integ.getUrl(), null), null,
                        "A integração não está publicada; entrega ignorada.", 0, false, null);
            }

            IntegrationRuntime rt = integrations.runtime(integ);
            WebhookSender.Result r = sender.send(d.tenant(), rt, d.body(), d.event(), d.deliveryId());

            boolean retry = !r.ok() && r.retryable() && !manual && n <= integ.getRetries();
            IntegrationLog saved = save(d, n, integ.getId(), integ.getName(),
                    r.ok() ? IntegrationLog.SUCCESS : IntegrationLog.ERROR,
                    r.httpStatus(), integ.getMethod(), r.logUrl(), r.responseBody(), r.error(),
                    r.durationMs(), retry, d.body());
            if (retry) {
                int delay = retryDelays.get(Math.min(n - 1, retryDelays.size() - 1));
                int next = n + 1;
                scheduler.schedule(() -> inTenant(d.tenant(), () -> attempt(d, next)),
                        Instant.now().plusSeconds(delay));
            }
            return saved;
        } catch (RuntimeException e) {
            log.error("Erro inesperado na entrega {} (tentativa {}): {}", d.deliveryId(), n, e.toString());
            return null;
        }
    }

    private IntegrationLog save(Delivery d, int attempt, Long integrationId, String integrationName, String status,
                                Integer httpStatus, String method, String url, String responseBody, String error,
                                int durationMs, boolean willRetry, String requestBody) {
        IntegrationLog l = new IntegrationLog();
        l.setDeliveryId(d.deliveryId());
        l.setAttempt(attempt);
        l.setIntegrationId(integrationId);
        l.setIntegrationName(integrationName == null ? "(sem nome)" : integrationName);
        l.setFormId(d.formId());
        l.setFormName(d.formName());
        l.setSubmissionId(d.submissionId());
        l.setEvent(d.event());
        l.setStatus(status);
        l.setHttpStatus(httpStatus);
        l.setMethod(method);
        l.setRequestUrl(url);
        l.setRequestBody(requestBody != null ? requestBody : d.body());
        l.setResponseBody(responseBody);
        l.setErrorMessage(error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error));
        l.setDurationMs(durationMs);
        l.setWillRetry(willRetry);
        l.setTriggeredBy(d.triggeredBy());
        return logs.save(l);
    }

    // ----- acoes manuais (requisicao do usuario; tenant ja no contexto) -----

    /** Envia um payload de exemplo (inclusive de rascunho) e devolve o log da tentativa. */
    public IntegrationLog sendTest(long integrationId, Long userId) {
        Integration integ = integrations.find(integrationId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Integração não encontrada."));
        String tenant = TenantContext.require();
        String body;
        try {
            body = mapper.writeValueAsString(payloads.sample(tenant));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao montar o payload de teste.");
        }
        Delivery d = new Delivery(tenant, UUID.randomUUID().toString(), integ.getId(), null, null, null,
                WebhookEvents.TEST, body, userId);
        return requireSaved(attempt(d, 1));
    }

    /** Reenvia o mesmo corpo de uma tentativa anterior (nova entrega, sem retentativas automaticas). */
    public IntegrationLog resend(long logId, Long userId) {
        IntegrationLog old = logs.findById(logId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de log não encontrado."));
        if (old.getIntegrationId() == null || integrations.find(old.getIntegrationId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A integração deste registro foi excluída.");
        }
        if (old.getRequestBody() == null || old.getRequestBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este registro não guarda o conteúdo enviado.");
        }
        Delivery d = new Delivery(TenantContext.require(), UUID.randomUUID().toString(), old.getIntegrationId(),
                old.getFormId(), old.getFormName(), old.getSubmissionId(), old.getEvent(),
                old.getRequestBody(), userId);
        return requireSaved(attempt(d, 1));
    }

    private static IntegrationLog requireSaved(IntegrationLog l) {
        if (l == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao executar o envio.");
        return l;
    }

    private static void inTenant(String tenant, Runnable r) {
        TenantContext.set(tenant);
        try {
            r.run();
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(WebhookDispatcher.class).error("Falha na tarefa de webhook: {}", e.toString());
        } finally {
            TenantContext.clear();
        }
    }
}
