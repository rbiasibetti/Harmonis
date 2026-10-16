package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.*;
import app.harmonis.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Cadastro de integracoes e consulta de logs; cada recurso tem sua propria permissao. */
@RestController
@RequestMapping("/api")
public class IntegrationController {

    private final IntegrationService service;
    private final HookBindingService bindings;
    private final WebhookDispatcher dispatcher;
    private final IntegrationLogService logService;

    public IntegrationController(IntegrationService service, HookBindingService bindings,
                                 WebhookDispatcher dispatcher, IntegrationLogService logService) {
        this.service = service;
        this.bindings = bindings;
        this.dispatcher = dispatcher;
        this.logService = logService;
    }

    // ----- cadastro -----

    @GetMapping("/integrations")
    @PreAuthorize("hasAuthority('integrations.view')")
    public List<IntegrationSummary> list() {
        return service.list();
    }

    /** Integracoes publicadas para escolher no fluxo (quem edita formularios nao precisa ver o cadastro). */
    @GetMapping("/integrations/options")
    @PreAuthorize("hasAnyAuthority('integrations.view', 'forms.update')")
    public List<IntegrationOption> options() {
        return service.options();
    }

    @GetMapping("/integrations/{id}")
    @PreAuthorize("hasAuthority('integrations.view')")
    public IntegrationView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/integrations/{id}/usage")
    @PreAuthorize("hasAuthority('integrations.view')")
    public List<UsageRef> usage(@PathVariable long id) {
        return bindings.usage(id);
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('integrations.create')")
    public IntegrationView create(@Valid @RequestBody SaveIntegrationRequest req,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return service.create(req, principal.userId());
    }

    @PutMapping("/integrations/{id}")
    @PreAuthorize("hasAuthority('integrations.update')")
    public IntegrationView update(@PathVariable long id, @Valid @RequestBody SaveIntegrationRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/integrations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('integrations.delete')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/integrations/{id}/publish")
    @PreAuthorize("hasAuthority('integrations.publish')")
    public IntegrationView publish(@PathVariable long id) {
        return service.publish(id);
    }

    @PostMapping("/integrations/{id}/unpublish")
    @PreAuthorize("hasAuthority('integrations.publish')")
    public IntegrationView unpublish(@PathVariable long id) {
        return service.unpublish(id);
    }

    /** Envia um payload de exemplo (funciona em rascunho) e devolve o resultado da tentativa. */
    @PostMapping("/integrations/{id}/test")
    @PreAuthorize("hasAuthority('integrations.update')")
    public LogDetail test(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return IntegrationLogService.toDetail(dispatcher.sendTest(id, principal.userId()));
    }

    // ----- logs -----

    @GetMapping("/integration-logs")
    @PreAuthorize("hasAuthority('integrations.logs')")
    public LogPage logs(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String event,
                        @RequestParam(required = false) Long integrationId,
                        @RequestParam(required = false) Long formId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size) {
        return logService.search(status, event, integrationId, formId, from, to, q, page, size);
    }

    @GetMapping("/integration-logs/stats")
    @PreAuthorize("hasAuthority('integrations.logs')")
    public LogStats stats() {
        return logService.stats();
    }

    @GetMapping("/integration-logs/{id}")
    @PreAuthorize("hasAuthority('integrations.logs')")
    public LogDetail logDetail(@PathVariable long id) {
        return logService.detail(id);
    }

    /** Reenvio manual exige ver o log e poder alterar integracoes. */
    @PostMapping("/integration-logs/{id}/resend")
    @PreAuthorize("hasAuthority('integrations.logs') and hasAuthority('integrations.update')")
    public LogDetail resend(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return IntegrationLogService.toDetail(dispatcher.resend(id, principal.userId()));
    }
}
