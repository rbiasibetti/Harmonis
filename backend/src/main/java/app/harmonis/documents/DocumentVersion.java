package app.harmonis.documents;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hm_document_versions")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String disk = "local";

    @Column(nullable = false)
    private String path;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(nullable = false)
    private long size = 0;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DocumentVersion() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getDisk() { return disk; }
    public void setDisk(String disk) { this.disk = disk; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
