-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Formularios + respostas. schema/approval_flow/data como JSON (NVARCHAR(MAX)).
-- Prefixo hm_.

IF OBJECT_ID(N'hm_forms', N'U') IS NULL
BEGIN
    CREATE TABLE hm_forms (
        id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name          NVARCHAR(200) NOT NULL,
        slug          NVARCHAR(200) NOT NULL,
        description   NVARCHAR(1000) NULL,
        schema_json   NVARCHAR(MAX) NULL CONSTRAINT ck_hm_forms_schema CHECK (schema_json IS NULL OR ISJSON(schema_json) = 1),
        approval_flow NVARCHAR(MAX) NULL CONSTRAINT ck_hm_forms_flow CHECK (approval_flow IS NULL OR ISJSON(approval_flow) = 1),
        status        NVARCHAR(20) NOT NULL CONSTRAINT df_hm_forms_status DEFAULT ('draft'),
        created_by    BIGINT NULL,
        created_at    DATETIME2 NOT NULL CONSTRAINT df_hm_forms_created DEFAULT (SYSUTCDATETIME()),
        updated_at    DATETIME2 NOT NULL CONSTRAINT df_hm_forms_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT uq_hm_forms_slug UNIQUE (slug),
        CONSTRAINT fk_hm_forms_creator FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
END
GO

IF OBJECT_ID(N'hm_form_submissions', N'U') IS NULL
BEGIN
    CREATE TABLE hm_form_submissions (
        id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_id      BIGINT NOT NULL,
        data_json    NVARCHAR(MAX) NULL CONSTRAINT ck_hm_sub_data CHECK (data_json IS NULL OR ISJSON(data_json) = 1),
        submitted_by BIGINT NULL,
        created_at   DATETIME2 NOT NULL CONSTRAINT df_hm_sub_created DEFAULT (SYSUTCDATETIME()),
        updated_at   DATETIME2 NOT NULL CONSTRAINT df_hm_sub_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_sub_form FOREIGN KEY (form_id) REFERENCES hm_forms (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_sub_user FOREIGN KEY (submitted_by) REFERENCES hm_users (id)
    );
END
GO
