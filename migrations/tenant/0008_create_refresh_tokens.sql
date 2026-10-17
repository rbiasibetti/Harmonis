-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Refresh tokens da sessao. O token em si e opaco (256 bits aleatorios) e NUNCA e gravado:
-- guarda-se apenas o hash SHA-256. Cada renovacao rotaciona o token (o anterior recebe used_at);
-- reapresentar um token ja usado fora da janela de tolerancia revoga toda a "familia" (sessao).
IF OBJECT_ID(N'hm_refresh_token', N'U') IS NULL
BEGIN
    CREATE TABLE hm_refresh_token (
        id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id           BIGINT NOT NULL,
        family_id         CHAR(36) NOT NULL,
        token_hash        CHAR(64) NOT NULL,
        expires_at        DATETIME2 NOT NULL,
        family_expires_at DATETIME2 NOT NULL,
        used_at           DATETIME2 NULL,
        revoked_at        DATETIME2 NULL,
        user_agent        NVARCHAR(300) NULL,
        created_at        DATETIME2 NOT NULL CONSTRAINT df_hm_refresh_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_refresh_user FOREIGN KEY (user_id) REFERENCES hm_users (id) ON DELETE CASCADE
    );
    CREATE UNIQUE INDEX ux_hm_refresh_hash ON hm_refresh_token (token_hash);
    CREATE INDEX ix_hm_refresh_family ON hm_refresh_token (family_id);
    CREATE INDEX ix_hm_refresh_user ON hm_refresh_token (user_id);
END
