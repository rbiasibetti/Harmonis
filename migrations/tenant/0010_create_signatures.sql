-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Assinaturas de formulario (campo tipo "signature"). A imagem (desenho ou selfie) fica
-- em disco (DocumentStorage); aqui ficam os metadados coletados no ato de assinar:
-- signatario (do usuario logado), carimbo de data/hora, IP, dispositivo (User-Agent) e
-- geolocalizacao (best-effort). submission_id fica nulo ate o envio confirmar. Prefixo hm_.

IF OBJECT_ID(N'hm_signature', N'U') IS NULL
BEGIN
    CREATE TABLE hm_signature (
        id             BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_id        BIGINT NOT NULL,
        submission_id  BIGINT NULL,
        field_key      NVARCHAR(100) NOT NULL,
        style          NVARCHAR(10) NOT NULL,              -- drawn | selfie
        stored_path    NVARCHAR(400) NOT NULL,             -- imagem em disco
        mime_type      NVARCHAR(150) NULL,
        size_bytes     BIGINT NOT NULL CONSTRAINT df_hm_sig_size DEFAULT (0),
        signer_user_id BIGINT NULL,
        signer_name    NVARCHAR(200) NULL,
        signer_email   NVARCHAR(260) NULL,
        signed_at      DATETIME2 NOT NULL CONSTRAINT df_hm_sig_signed DEFAULT (SYSUTCDATETIME()),
        ip_address     NVARCHAR(64) NULL,
        user_agent     NVARCHAR(500) NULL,
        geo_status     NVARCHAR(20) NOT NULL CONSTRAINT df_hm_sig_geo DEFAULT ('unknown'), -- ok | denied | unavailable | unknown
        geo_lat        FLOAT NULL,
        geo_lon        FLOAT NULL,
        geo_accuracy   FLOAT NULL,
        created_at     DATETIME2 NOT NULL CONSTRAINT df_hm_sig_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_sig_form FOREIGN KEY (form_id) REFERENCES hm_forms (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_sig_sub FOREIGN KEY (submission_id) REFERENCES hm_form_submissions (id),
        CONSTRAINT fk_hm_sig_user FOREIGN KEY (signer_user_id) REFERENCES hm_users (id),
        CONSTRAINT ck_hm_sig_style CHECK (style IN ('drawn', 'selfie'))
    );
    CREATE INDEX ix_hm_sig_form ON hm_signature (form_id);
    CREATE INDEX ix_hm_sig_sub ON hm_signature (submission_id);
END
GO
