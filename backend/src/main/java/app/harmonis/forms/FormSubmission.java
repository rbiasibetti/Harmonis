package app.harmonis.forms;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hm_form_submissions")
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    /** JSON { "<fieldKey>": valor } armazenado como texto. */
    @Column(name = "data_json", columnDefinition = "NVARCHAR(MAX)")
    private String dataJson;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FormSubmission() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
