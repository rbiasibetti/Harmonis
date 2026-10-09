package app.harmonis.notifications;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void notify(Long userId, String type, String title, String message, String url, Long instanceId) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setUrl(url);
        n.setInstanceId(instanceId);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(Long userId) {
        return repo.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repo.findById(id)
                .filter(n -> n.getUserId().equals(userId) && n.getReadAt() == null)
                .ifPresent(n -> { n.setReadAt(Instant.now()); repo.save(n); });
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> unread = repo.findByUserIdAndReadAtIsNull(userId);
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        repo.saveAll(unread);
    }
}
