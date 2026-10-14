package app.harmonis.workflow;

import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.security.AuthPrincipal;
import app.harmonis.workflow.ProcessDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/processes")
@PreAuthorize("hasAuthority('processes.view')")
public class ProcessController {

    private final ProcessService service;
    private final UserRepository users;

    public ProcessController(ProcessService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    private User currentUser(AuthPrincipal principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário inválido."));
    }

    @GetMapping("/inbox")
    public List<InboxItem> inbox(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.inbox(currentUser(principal));
    }

    @GetMapping("/pending-count")
    public Map<String, Object> pendingCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", service.inbox(currentUser(principal)).size());
    }

    @GetMapping("/mine")
    public List<MineItem> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.mine(principal.userId());
    }

    @GetMapping("/{id}")
    public ProcessDetail detail(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return service.detail(id, currentUser(principal));
    }

    @PostMapping("/tasks/{taskId}/decide")
    public void decide(@PathVariable long taskId,
                       @Valid @RequestBody DecideRequest req,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        service.decide(taskId, req.approved(), req.comment(), req.fieldValues(), currentUser(principal));
    }
}
