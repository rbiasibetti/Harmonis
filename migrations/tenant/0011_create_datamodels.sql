-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Modelos de dados (datamodels): fontes de consulta pre-cadastradas para campos de formulario
-- do tipo "lookup". A fonte pode ser:
--   static -> linhas gravadas em hm_datamodel_row (importadas de CSV e/ou editadas a mao);
--   query  -> uma consulta SELECT (somente leitura) a outras tabelas do banco do tenant.
-- columns_json/filters_json guardam a definicao de colunas e campos de filtro. Prefixo hm_.

IF OBJECT_ID(N'hm_datamodel', N'U') IS NULL
BEGIN
    CREATE TABLE hm_datamodel (
        id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name         NVARCHAR(150) NOT NULL,
        description  NVARCHAR(500) NULL,
        source_type  NVARCHAR(20) NOT NULL CONSTRAINT df_hm_dm_source DEFAULT ('static'),
        query_sql    NVARCHAR(MAX) NULL,
        columns_json NVARCHAR(MAX) NOT NULL,
        filters_json NVARCHAR(MAX) NULL,
        label_key    NVARCHAR(100) NULL,
        status       NVARCHAR(20) NOT NULL CONSTRAINT df_hm_dm_status DEFAULT ('draft'),
        created_by   BIGINT NULL,
        created_at   DATETIME2 NOT NULL CONSTRAINT df_hm_dm_created DEFAULT (SYSUTCDATETIME()),
        updated_at   DATETIME2 NOT NULL CONSTRAINT df_hm_dm_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT ck_hm_dm_source CHECK (source_type IN ('static', 'query')),
        CONSTRAINT ck_hm_dm_columns CHECK (ISJSON(columns_json) = 1),
        CONSTRAINT ck_hm_dm_filters CHECK (filters_json IS NULL OR ISJSON(filters_json) = 1),
        CONSTRAINT fk_hm_dm_user FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_dm_status ON hm_datamodel (status);
END
GO

IF OBJECT_ID(N'hm_datamodel_row', N'U') IS NULL
BEGIN
    CREATE TABLE hm_datamodel_row (
        id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        datamodel_id BIGINT NOT NULL,
        data_json    NVARCHAR(MAX) NOT NULL,
        created_at   DATETIME2 NOT NULL CONSTRAINT df_hm_dmr_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_dmr_model FOREIGN KEY (datamodel_id) REFERENCES hm_datamodel (id) ON DELETE CASCADE,
        CONSTRAINT ck_hm_dmr_data CHECK (ISJSON(data_json) = 1)
    );
    CREATE INDEX ix_hm_dmr_model ON hm_datamodel_row (datamodel_id);
END
GO
