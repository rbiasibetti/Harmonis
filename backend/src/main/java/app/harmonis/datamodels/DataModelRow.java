package app.harmonis.datamodels;

import jakarta.persistence.*;

import java.time.Instant;

/** Uma linha de um modelo de dados estatico (source_type = static). */
@Entity
@Table(name = "hm_datamodel_row")
public class DataModelRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datamodel_id", nullable = false)
    private Long dataModelId;

    @Column(name = "data_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String dataJson;

    @Column(name = "created_at")
    private Instant createdAt;

    public DataModelRow() {}

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getDataModelId() { return dataModelId; }
    public void setDataModelId(Long dataModelId) { this.dataModelId = dataModelId; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
}
