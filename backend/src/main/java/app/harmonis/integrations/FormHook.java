package app.harmonis.integrations;

import jakarta.persistence.*;

/** Vinculo formulario x evento x integracao (quando disparar qual webhook). */
@Entity
@Table(name = "hm_form_hook")
public class FormHook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(nullable = false, length = 30)
    private String event;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public FormHook() {}

    public FormHook(Long formId, String event, Long integrationId, int sortOrder) {
        this.formId = formId;
        this.event = event;
        this.integrationId = integrationId;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getFormId() { return formId; }
    public String getEvent() { return event; }
    public Long getIntegrationId() { return integrationId; }
    public int getSortOrder() { return sortOrder; }
}
