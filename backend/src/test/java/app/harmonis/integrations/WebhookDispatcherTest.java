package app.harmonis.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookDispatcherTest {

    private IntegrationService integrations;
    private WebhookSender sender;
    private IntegrationLogRepository logs;
    private ThreadPoolTaskScheduler scheduler;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        integrations = mock(IntegrationService.class);
        sender = mock(WebhookSender.class);
        logs = mock(IntegrationLogRepository.class);
        scheduler = mock(ThreadPoolTaskScheduler.class);
        when(logs.save(any(IntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        dispatcher = new WebhookDispatcher(integrations, mock(HookBindingService.class),
                mock(WebhookPayloadBuilder.class), sender, logs, mock(app.harmonis.forms.FormRepository.class),
                new ObjectMapper(), scheduler, List.of(5, 30));
    }

    private Integration integration(boolean published, int retries) {
        Integration i = new Integration();
        i.setName("ERP");
        i.setUrl("https://erp.exemplo.com/hook");
        i.setStatus(published ? Integration.PUBLISHED : Integration.DRAFT);
        i.setRetries(retries);
        // id e gerado pelo banco; reflexao para o teste
        try {
            var f = Integration.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(i, 3L);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return i;
    }

    private WebhookDispatcher.Delivery delivery(Long triggeredBy) {
        return new WebhookDispatcher.Delivery("acme", "del-1", 3L, 10L, "Compra", 55L,
                WebhookEvents.APPROVED, "{\"k\":1}", triggeredBy);
    }

    private void givenIntegration(Integration i) {
        when(integrations.find(3L)).thenReturn(Optional.of(i));
        when(integrations.runtime(i)).thenReturn(mock(IntegrationRuntime.class));
    }

    private static WebhookSender.Result ok() {
        return new WebhookSender.Result(true, 200, "{}", null, 12, "https://erp.exemplo.com/hook", false);
    }

    private static WebhookSender.Result fail(Integer status, boolean retryable) {
        return new WebhookSender.Result(false, status, "erro", "falhou", 30, "https://erp.exemplo.com/hook", retryable);
    }

    private IntegrationLog lastLog() {
        ArgumentCaptor<IntegrationLog> c = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(logs, atLeastOnce()).save(c.capture());
        return c.getValue();
    }

    @Test
    void sucesso_gera_log_success_e_nao_reagenda() {
        givenIntegration(integration(true, 2));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(ok());

        dispatcher.attempt(delivery(null), 1);

        IntegrationLog l = lastLog();
        assertEquals("success", l.getStatus());
        assertEquals(200, l.getHttpStatus());
        assertEquals(1, l.getAttempt());
        assertEquals("approved", l.getEvent());
        assertEquals("Compra", l.getFormName());
        assertEquals("{\"k\":1}", l.getRequestBody());
        assertFalse(l.isWillRetry());
        verifyNoInteractions(scheduler);
    }

    @Test
    void falha_transitoria_reagenda_com_a_espera_configurada() {
        givenIntegration(integration(true, 2));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(fail(503, true));

        dispatcher.attempt(delivery(null), 1);

        assertTrue(lastLog().isWillRetry());
        assertEquals("error", lastLog().getStatus());
        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), when.capture());
        long secs = when.getValue().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(secs >= 3 && secs <= 6, "1a espera = 5s, foi " + secs);
    }

    @Test
    void segunda_retentativa_usa_a_segunda_espera_e_a_ultima_para_de_reagendar() {
        givenIntegration(integration(true, 2));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(fail(500, true));

        dispatcher.attempt(delivery(null), 2);
        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), when.capture());
        long secs = when.getValue().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(secs >= 28 && secs <= 31, "2a espera = 30s, foi " + secs);

        reset(scheduler);
        dispatcher.attempt(delivery(null), 3); // tentativas = 1 + retries(2) => a 3a e a ultima
        assertFalse(lastLog().isWillRetry());
        verifyNoInteractions(scheduler);
    }

    @Test
    void erro_nao_retentavel_nao_reagenda() {
        givenIntegration(integration(true, 5));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(fail(400, false));

        dispatcher.attempt(delivery(null), 1);

        assertFalse(lastLog().isWillRetry());
        verifyNoInteractions(scheduler);
    }

    @Test
    void sem_retentativas_configuradas_nao_reagenda() {
        givenIntegration(integration(true, 0));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(fail(500, true));

        dispatcher.attempt(delivery(null), 1);

        verifyNoInteractions(scheduler);
    }

    @Test
    void integracao_nao_publicada_e_ignorada_e_registrada_sem_enviar() {
        givenIntegration(integration(false, 2));

        dispatcher.attempt(delivery(null), 1);

        IntegrationLog l = lastLog();
        assertEquals("skipped", l.getStatus());
        assertTrue(l.getErrorMessage().contains("não está publicada"));
        verifyNoInteractions(sender);
    }

    @Test
    void integracao_excluida_e_ignorada_e_registrada() {
        when(integrations.find(3L)).thenReturn(Optional.empty());

        dispatcher.attempt(delivery(null), 1);

        assertEquals("skipped", lastLog().getStatus());
        verifyNoInteractions(sender);
    }

    @Test
    void envio_manual_vale_para_rascunho_e_nunca_reagenda() {
        givenIntegration(integration(false, 3));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn(fail(500, true));

        IntegrationLog l = dispatcher.attempt(delivery(9L), 1);

        verify(sender).send(any(), any(), any(), any(), any());
        assertEquals(9L, l.getTriggeredBy());
        assertFalse(l.isWillRetry());
        verifyNoInteractions(scheduler);
    }

    @Test
    void excecao_inesperada_nao_derruba_a_thread() {
        givenIntegration(integration(true, 2));
        when(sender.send(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() -> dispatcher.attempt(delivery(null), 1));
    }
}
