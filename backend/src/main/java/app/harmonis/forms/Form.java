package app.harmonis.forms;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hm_forms")
public class Form {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    /** JSON {"fields":[...]} armazenado como texto (NVARCHAR(MAX)). */
    @Column(name = "schema_json", columnDefinition = "NVARCHAR(MAX)")
    private String schemaJson;

    /** Arvore de aprovacao (JSON). Preenchida no M3. */
    @Column(name = "approval_flow", columnDefinition = "NVARCHAR(MAX)")
    private String approvalFlow;

    @Column(nullable = false)
    private String status = "draft";

    /** Idiomas aos quais o formulario esta vinculado (CSV: pt-BR,en,es). */
    @Column(nullable = false)
    private String languages = "pt-BR,en,es";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Form() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    public boolean isPublished() {
        return "published".equals(status);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getApprovalFlow() { return approvalFlow; }
    public void setApprovalFlow(String approvalFlow) { this.approvalFlow = approvalFlow; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
