-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Portal/CMS: paginas em Markdown (rascunho/publicado). Prefixo hm_.

IF OBJECT_ID(N'hm_pages', N'U') IS NULL
BEGIN
    CREATE TABLE hm_pages (
        id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        title        NVARCHAR(300) NOT NULL,
        slug         NVARCHAR(300) NOT NULL,
        excerpt      NVARCHAR(500) NULL,
        content      NVARCHAR(MAX) NULL,
        status       NVARCHAR(20) NOT NULL CONSTRAINT df_hm_pages_status DEFAULT ('draft'),
        published_at DATETIME2 NULL,
        created_by   BIGINT NULL,
        created_at   DATETIME2 NOT NULL CONSTRAINT df_hm_pages_created DEFAULT (SYSUTCDATETIME()),
        updated_at   DATETIME2 NOT NULL CONSTRAINT df_hm_pages_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT uq_hm_pages_slug UNIQUE (slug),
        CONSTRAINT fk_hm_pages_creator FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
END
GO
