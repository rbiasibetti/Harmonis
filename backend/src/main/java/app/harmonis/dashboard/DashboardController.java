package app.harmonis.dashboard;

import app.harmonis.documents.DocumentService;
import app.harmonis.forms.FormService;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.users.UserService;
import app.harmonis.portal.PageService;
import app.harmonis.security.AuthPrincipal;
import app.harmonis.workflow.ApprovalService;
import app.harmonis.workflow.ProcessDtos.InboxItem;
import app.harmonis.workflow.ProcessDtos.MineItem;
import app.harmonis.workflow.ProcessInstanceRepository;
import app.harmonis.workflow.ProcessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Metricas reais do workspace, gated por permissao do usuario. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserRepository users;
    private final ApprovalService approvals;
    private final ProcessService processes;
    private final ProcessInstanceRepository instances;
    private final FormService forms;
    private final DocumentService documents;
    private final PageService pages;
    private final UserService userService;

    public DashboardController(UserRepository users, ApprovalService approvals, ProcessService processes,
                               ProcessInstanceRepository instances, FormService forms,
                               DocumentService documents, PageService pages, UserService userService) {
        this.users = users;
        this.approvals = approvals;
        this.processes = processes;
        this.instances = instances;
        this.forms = forms;
        this.documents = documents;
        this.pages = pages;
        this.userService = userService;
    }

    public record Metrics(
            Long pendingApprovals, Long activeProcesses, Long publishedForms,
            Long documents, Long publishedPages, Long activeUsers
    ) {}

    public record DashboardView(
            Metrics metrics,
            List<InboxItem> pendingApprovals,
            List<MineItem> myRequests,
            List<FormService.RecentSubmission> recentSubmissions
    ) {}

    @GetMapping
    public DashboardView get(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário inválido."));

        boolean canProcesses = can(user, "processes.view");
        boolean canForms = can(user, "forms.view");
        boolean canSubmissions = can(user, "forms.submissions");
        boolean canDocs = can(user, "documents.view");
        boolean canPages = can(user, "pages.view");
        boolean canUsers = can(user, "users.view");

        Metrics metrics = new Metrics(
                canProcesses ? (long) approvals.pendingApprovalsFor(user).size() : null,
                canProcesses ? instances.countByStatus("pending") : null,
                canForms ? forms.countPublished() : null,
                canDocs ? documents.countDocuments() : null,
                canPages ? pages.countPublished() : null,
                canUsers ? userService.countActive() : null
        );

        List<InboxItem> inbox = canProcesses ? processes.inbox(user).stream().limit(5).toList() : List.of();
        List<MineItem> mine = canProcesses ? processes.mine(user.getId()).stream().limit(5).toList() : List.of();
        List<FormService.RecentSubmission> recent = canSubmissions ? forms.recentSubmissions() : List.of();

        return new DashboardView(metrics, inbox, mine, recent);
    }

    private boolean can(User user, String permission) {
        return user.isOwner() || user.permissionNames().contains(permission);
    }
}
