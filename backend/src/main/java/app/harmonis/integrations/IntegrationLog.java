package app.harmonis.integrations;

import jakarta.persistence.*;

import java.time.Instant;

/** Uma tentativa de entrega de webhook (success | error | skipped). */
@Entity
@Table(name = "hm_integration_log")
public class IntegrationLog {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String SKIPPED = "skipped";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, length = 40)
    private String deliveryId;

    @Column(nullable = false)
    private int attempt = 1;

    @Column(name = "integration_id")
    private Long integrationId;

    @Column(name = "integration_name", nullable = false, length = 150)
    private String integrationName;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "form_name", length = 255)
    private String formName;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(nullable = false, length = 30)
    private String event;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(length = 10)
    private String method;

    @Column(name = "request_url", length = 1000)
    private String requestUrl;

    @Column(name = "request_body", columnDefinition = "NVARCHAR(MAX)")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "NVARCHAR(MAX)")
    private String responseBody;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "will_retry", nullable = false)
    private boolean willRetry;

    @Column(name = "triggered_by")
    private Long triggeredBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public IntegrationLog() {}

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public Long getIntegrationId() { return integrationId; }
    public void setIntegrationId(Long integrationId) { this.integrationId = integrationId; }
    public String getIntegrationName() { return integrationName; }
    public void setIntegrationName(String integrationName) { this.integrationName = integrationName; }
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public boolean isWillRetry() { return willRetry; }
    public void setWillRetry(boolean willRetry) { this.willRetry = willRetry; }
    public Long getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(Long triggeredBy) { this.triggeredBy = triggeredBy; }
    public Instant getCreatedAt() { return createdAt; }
}
