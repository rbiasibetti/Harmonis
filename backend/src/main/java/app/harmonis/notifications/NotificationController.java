package app.harmonis.notifications;

import app.harmonis.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record NotificationView(Long id, String type, String title, String message,
                                   String url, Long instanceId, Instant readAt, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getUrl(), n.getInstanceId(), n.getReadAt(), n.getCreatedAt());
        }
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthPrincipal principal) {
        List<NotificationView> items = service.recent(principal.userId()).stream()
                .map(NotificationView::from).toList();
        return Map.of("items", items, "unread", service.unread(principal.userId()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        service.markRead(principal.userId(), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        service.markAllRead(principal.userId());
    }
}
