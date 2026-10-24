package app.harmonis.datamodels;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Modelo de dados pre-cadastrado: fonte de consulta para campos de formulario do tipo "lookup".
 * source_type = static (linhas em hm_datamodel_row, de CSV/edicao manual) ou query (SELECT a
 * outras tabelas do banco do tenant). columns/filters guardam a definicao como JSON.
 */
@Entity
@Table(name = "hm_datamodel")
public class DataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType = "static";

    @Column(name = "query_sql", columnDefinition = "NVARCHAR(MAX)")
    private String querySql;

    @Column(name = "columns_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String columnsJson;

    @Column(name = "filters_json", columnDefinition = "NVARCHAR(MAX)")
    private String filtersJson;

    @Column(name = "label_key", length = 100)
    private String labelKey;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DataModel() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public boolean isPublished() { return "published".equals(status); }
    public boolean isQuery() { return "query".equals(sourceType); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getQuerySql() { return querySql; }
    public void setQuerySql(String querySql) { this.querySql = querySql; }
    public String getColumnsJson() { return columnsJson; }
    public void setColumnsJson(String columnsJson) { this.columnsJson = columnsJson; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public String getLabelKey() { return labelKey; }
    public void setLabelKey(String labelKey) { this.labelKey = labelKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
