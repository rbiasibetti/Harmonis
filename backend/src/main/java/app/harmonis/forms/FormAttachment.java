package app.harmonis.forms;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Arquivo anexado a um formulario. Enviado antes da submissao (submission_id nulo) e
 * "confirmado" no envio/decisao, quando recebe submission_id + field_key. Os bytes ficam
 * em disco privado (DocumentStorage); aqui guardamos apenas os metadados e o caminho relativo.
 */
@Entity
@Table(name = "hm_form_attachment")
public class FormAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "field_key", length = 100)
    private String fieldKey;

    @Column(name = "stored_path", nullable = false, length = 400)
    private String storedPath;

    @Column(name = "original_name", nullable = false, length = 260)
    private String originalName;

    @Column(name = "mime_type", length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public FormAttachment() {}

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
