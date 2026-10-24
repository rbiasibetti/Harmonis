package app.harmonis.forms;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Uma assinatura coletada em um campo "signature". A imagem (desenho ou selfie) fica em
 * disco (DocumentStorage); aqui ficam os metadados do ato de assinar. submission_id fica
 * nulo ate o envio confirmar a assinatura.
 */
@Entity
@Table(name = "hm_signature")
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    /** drawn | selfie */
    @Column(nullable = false, length = 10)
    private String style;

    @Column(name = "stored_path", nullable = false, length = 400)
    private String storedPath;

    @Column(name = "mime_type", length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "signer_user_id")
    private Long signerUserId;

    @Column(name = "signer_name", length = 200)
    private String signerName;

    @Column(name = "signer_email", length = 260)
    private String signerEmail;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt = Instant.now();

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** ok | denied | unavailable | unknown */
    @Column(name = "geo_status", nullable = false, length = 20)
    private String geoStatus = "unknown";

    @Column(name = "geo_lat")
    private Double geoLat;

    @Column(name = "geo_lon")
    private Double geoLon;

    @Column(name = "geo_accuracy")
    private Double geoAccuracy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Signature() {}

    public Long getId() { return id; }
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Long getSignerUserId() { return signerUserId; }
    public void setSignerUserId(Long signerUserId) { this.signerUserId = signerUserId; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getGeoStatus() { return geoStatus; }
    public void setGeoStatus(String geoStatus) { this.geoStatus = geoStatus; }
    public Double getGeoLat() { return geoLat; }
    public void setGeoLat(Double geoLat) { this.geoLat = geoLat; }
    public Double getGeoLon() { return geoLon; }
    public void setGeoLon(Double geoLon) { this.geoLon = geoLon; }
    public Double getGeoAccuracy() { return geoAccuracy; }
    public void setGeoAccuracy(Double geoAccuracy) { this.geoAccuracy = geoAccuracy; }
}
