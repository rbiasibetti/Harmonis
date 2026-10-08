-- Contexto: harmonis_catalog
-- Registro mestre de tenants. Roteia o login para o banco de cada cliente.

IF OBJECT_ID(N'hm_tenants', N'U') IS NULL
BEGIN
    CREATE TABLE hm_tenants (
        id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name       NVARCHAR(200) NOT NULL,
        slug       NVARCHAR(100) NOT NULL,
        db_name    NVARCHAR(200) NOT NULL,
        [plan]     NVARCHAR(50)  NOT NULL CONSTRAINT df_hm_tenants_plan DEFAULT ('free'),
        is_active  BIT           NOT NULL CONSTRAINT df_hm_tenants_active DEFAULT (1),
        created_at DATETIME2     NOT NULL CONSTRAINT df_hm_tenants_created DEFAULT (SYSUTCDATETIME()),
        updated_at DATETIME2     NOT NULL CONSTRAINT df_hm_tenants_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT uq_hm_tenants_slug UNIQUE (slug)
    );
END
GO

-- Tenant de demonstracao
IF NOT EXISTS (SELECT 1 FROM hm_tenants WHERE slug = 'acme')
BEGIN
    INSERT INTO hm_tenants (name, slug, db_name, [plan], is_active)
    VALUES (N'Acme', N'acme', N'harmonis_acme', N'free', 1);
END
GO
