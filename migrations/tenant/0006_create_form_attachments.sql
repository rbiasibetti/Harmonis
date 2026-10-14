-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Anexos de formulario. Os bytes ficam em disco (DocumentStorage); aqui so os metadados.
-- submission_id fica nulo ate o envio/decisao confirmar o anexo. Prefixo hm_.

IF OBJECT_ID(N'hm_form_attachment', N'U') IS NULL
BEGIN
    CREATE TABLE hm_form_attachment (
        id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_id       BIGINT NOT NULL,
        submission_id BIGINT NULL,
        field_key     NVARCHAR(100) NULL,
        stored_path   NVARCHAR(400) NOT NULL,
        original_name NVARCHAR(260) NOT NULL,
        mime_type     NVARCHAR(150) NULL,
        size_bytes    BIGINT NOT NULL CONSTRAINT df_hm_form_att_size DEFAULT (0),
        uploaded_by   BIGINT NULL,
        created_at    DATETIME2 NOT NULL CONSTRAINT df_hm_form_att_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_form_att_form FOREIGN KEY (form_id) REFERENCES hm_forms (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_form_att_sub FOREIGN KEY (submission_id) REFERENCES hm_form_submissions (id),
        CONSTRAINT fk_hm_form_att_user FOREIGN KEY (uploaded_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_form_att_form ON hm_form_attachment (form_id);
    CREATE INDEX ix_hm_form_att_sub ON hm_form_attachment (submission_id);
END
GO
