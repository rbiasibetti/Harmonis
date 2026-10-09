package app.harmonis.notifications;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hm_notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    private String message;
    private String url;

    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public Notification() {}

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
