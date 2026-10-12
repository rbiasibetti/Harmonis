-- Contexto: banco do tenant (ex.: harmonis_acme)
-- ECM/GED: pastas hierarquicas, documentos com versionamento e metadados. Prefixo hm_.

IF OBJECT_ID(N'hm_folders', N'U') IS NULL
BEGIN
    CREATE TABLE hm_folders (
        id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        parent_id  BIGINT NULL,
        name       NVARCHAR(200) NOT NULL,
        created_by BIGINT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT df_hm_folders_created DEFAULT (SYSUTCDATETIME()),
        updated_at DATETIME2 NOT NULL CONSTRAINT df_hm_folders_updated DEFAULT (SYSUTCDATETIME()),
        -- sem ON DELETE CASCADE no self-ref (SQL Server nao permite); exclusao recursiva na aplicacao
        CONSTRAINT fk_hm_folders_parent FOREIGN KEY (parent_id) REFERENCES hm_folders (id),
        CONSTRAINT fk_hm_folders_creator FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_folders_parent ON hm_folders (parent_id);
END
GO

IF OBJECT_ID(N'hm_documents', N'U') IS NULL
BEGIN
    CREATE TABLE hm_documents (
        id              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        folder_id       BIGINT NULL,
        name            NVARCHAR(300) NOT NULL,
        description     NVARCHAR(1000) NULL,
        metadata_json   NVARCHAR(MAX) NULL CONSTRAINT ck_hm_docs_meta CHECK (metadata_json IS NULL OR ISJSON(metadata_json) = 1),
        current_version INT NOT NULL CONSTRAINT df_hm_docs_curver DEFAULT (1),
        created_by      BIGINT NULL,
        created_at      DATETIME2 NOT NULL CONSTRAINT df_hm_docs_created DEFAULT (SYSUTCDATETIME()),
        updated_at      DATETIME2 NOT NULL CONSTRAINT df_hm_docs_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_docs_folder FOREIGN KEY (folder_id) REFERENCES hm_folders (id),
        CONSTRAINT fk_hm_docs_creator FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_docs_folder ON hm_documents (folder_id);
END
GO

IF OBJECT_ID(N'hm_document_versions', N'U') IS NULL
BEGIN
    CREATE TABLE hm_document_versions (
        id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        document_id   BIGINT NOT NULL,
        version       INT NOT NULL,
        disk          NVARCHAR(50) NOT NULL CONSTRAINT df_hm_dv_disk DEFAULT ('local'),
        path          NVARCHAR(500) NOT NULL,
        original_name NVARCHAR(300) NOT NULL,
        mime_type     NVARCHAR(150) NULL,
        size          BIGINT NOT NULL CONSTRAINT df_hm_dv_size DEFAULT (0),
        uploaded_by   BIGINT NULL,
        created_at    DATETIME2 NOT NULL CONSTRAINT df_hm_dv_created DEFAULT (SYSUTCDATETIME()),
        updated_at    DATETIME2 NOT NULL CONSTRAINT df_hm_dv_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_dv_document FOREIGN KEY (document_id) REFERENCES hm_documents (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_dv_uploader FOREIGN KEY (uploaded_by) REFERENCES hm_users (id),
        CONSTRAINT uq_hm_dv_doc_version UNIQUE (document_id, version)
    );
END
GO
