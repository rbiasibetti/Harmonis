package app.harmonis.integrations;

import jakarta.persistence.*;

import java.time.Instant;

/** Webhook de saida cadastrado. Segredos ficam cifrados em auth_json/headers_json/signing_secret. */
@Entity
@Table(name = "hm_integration")
public class Integration {

    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(nullable = false, length = 10)
    private String method = "POST";

    @Column(name = "auth_type", nullable = false, length = 30)
    private String authType = "none";

    @Column(name = "auth_json", columnDefinition = "NVARCHAR(MAX)")
    private String authJson;

    @Column(name = "headers_json", columnDefinition = "NVARCHAR(MAX)")
    private String headersJson;

    @Column(name = "signing_secret", length = 600)
    private String signingSecret;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 15;

    @Column(nullable = false)
    private int retries = 2;

    @Column(nullable = false, length = 20)
    private String status = DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Integration() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public boolean isPublished() { return PUBLISHED.equals(status); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthJson() { return authJson; }
    public void setAuthJson(String authJson) { this.authJson = authJson; }
    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
