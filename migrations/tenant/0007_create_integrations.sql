-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Integracoes (webhooks de saida): cadastro reutilizavel, vinculos por formulario/evento
-- e log de entregas. Segredos (senhas, tokens, chaves) ficam CIFRADOS pela aplicacao
-- (AES-GCM) dentro de auth_json / headers_json / signing_secret. Prefixo hm_.

IF OBJECT_ID(N'hm_integration', N'U') IS NULL
BEGIN
    CREATE TABLE hm_integration (
        id              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name            NVARCHAR(150) NOT NULL,
        description     NVARCHAR(500) NULL,
        url             NVARCHAR(1000) NOT NULL,
        method          NVARCHAR(10) NOT NULL CONSTRAINT df_hm_integration_method DEFAULT ('POST'),
        auth_type       NVARCHAR(30) NOT NULL CONSTRAINT df_hm_integration_auth DEFAULT ('none'),
        auth_json       NVARCHAR(MAX) NULL,
        headers_json    NVARCHAR(MAX) NULL,
        signing_secret  NVARCHAR(600) NULL,
        timeout_seconds INT NOT NULL CONSTRAINT df_hm_integration_timeout DEFAULT (15),
        retries         INT NOT NULL CONSTRAINT df_hm_integration_retries DEFAULT (2),
        status          NVARCHAR(20) NOT NULL CONSTRAINT df_hm_integration_status DEFAULT ('draft'),
        published_at    DATETIME2 NULL,
        created_by      BIGINT NULL,
        created_at      DATETIME2 NOT NULL CONSTRAINT df_hm_integration_created DEFAULT (SYSUTCDATETIME()),
        updated_at      DATETIME2 NOT NULL CONSTRAINT df_hm_integration_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT ck_hm_integration_auth_json CHECK (auth_json IS NULL OR ISJSON(auth_json) = 1),
        CONSTRAINT ck_hm_integration_headers_json CHECK (headers_json IS NULL OR ISJSON(headers_json) = 1),
        CONSTRAINT fk_hm_integration_user FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_integration_status ON hm_integration (status);
END
GO

-- Vinculo formulario x evento x integracao. event: submitted | approved | rejected.
-- A integracao so pode ser excluida se nao houver vinculo (FK sem cascade).
IF OBJECT_ID(N'hm_form_hook', N'U') IS NULL
BEGIN
    CREATE TABLE hm_form_hook (
        id             BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_id        BIGINT NOT NULL,
        event          NVARCHAR(30) NOT NULL,
        integration_id BIGINT NOT NULL,
        sort_order     INT NOT NULL CONSTRAINT df_hm_form_hook_order DEFAULT (0),
        CONSTRAINT fk_hm_form_hook_form FOREIGN KEY (form_id) REFERENCES hm_forms (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_form_hook_integration FOREIGN KEY (integration_id) REFERENCES hm_integration (id),
        CONSTRAINT uq_hm_form_hook UNIQUE (form_id, event, integration_id)
    );
    CREATE INDEX ix_hm_form_hook_integration ON hm_form_hook (integration_id);
END
GO

-- Uma linha por TENTATIVA de entrega (delivery_id agrupa as tentativas de uma mesma entrega).
-- Preserva historico mesmo se a integracao/formulario forem excluidos (nome e URL em snapshot).
IF OBJECT_ID(N'hm_integration_log', N'U') IS NULL
BEGIN
    CREATE TABLE hm_integration_log (
        id               BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        delivery_id      NVARCHAR(40) NOT NULL,
        attempt          INT NOT NULL CONSTRAINT df_hm_integration_log_attempt DEFAULT (1),
        integration_id   BIGINT NULL,
        integration_name NVARCHAR(150) NOT NULL,
        form_id          BIGINT NULL,
        form_name        NVARCHAR(255) NULL,
        submission_id    BIGINT NULL,
        event            NVARCHAR(30) NOT NULL,
        status           NVARCHAR(20) NOT NULL,   -- success | error | skipped
        http_status      INT NULL,
        method           NVARCHAR(10) NULL,
        request_url      NVARCHAR(1000) NULL,     -- sem segredos (query de api key mascarada)
        request_body     NVARCHAR(MAX) NULL,
        response_body    NVARCHAR(MAX) NULL,      -- truncado pela aplicacao
        error_message    NVARCHAR(1000) NULL,
        duration_ms      INT NULL,
        will_retry       BIT NOT NULL CONSTRAINT df_hm_integration_log_retry DEFAULT (0),
        triggered_by     BIGINT NULL,             -- usuario que reenviou/testou manualmente
        created_at       DATETIME2 NOT NULL CONSTRAINT df_hm_integration_log_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_integration_log_integration FOREIGN KEY (integration_id)
            REFERENCES hm_integration (id) ON DELETE SET NULL
    );
    CREATE INDEX ix_hm_integration_log_created ON hm_integration_log (created_at DESC);
    CREATE INDEX ix_hm_integration_log_integration ON hm_integration_log (integration_id);
    CREATE INDEX ix_hm_integration_log_delivery ON hm_integration_log (delivery_id);
    CREATE INDEX ix_hm_integration_log_status ON hm_integration_log (status);
END
GO
