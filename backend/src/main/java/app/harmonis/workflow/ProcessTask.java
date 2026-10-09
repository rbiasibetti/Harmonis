package app.harmonis.workflow;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hm_process_tasks")
public class ProcessTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_instance_id", nullable = false)
    private ProcessInstance instance;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "approver_type", nullable = false)
    private String approverType = "role";

    private String role;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ProcessTask() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public boolean isPending() { return "pending".equals(status); }

    public Long getId() { return id; }
    public ProcessInstance getInstance() { return instance; }
    public void setInstance(ProcessInstance instance) { this.instance = instance; }
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getApproverType() { return approverType; }
    public void setApproverType(String approverType) { this.approverType = approverType; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
