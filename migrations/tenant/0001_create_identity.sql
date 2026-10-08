-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Identidade + RBAC. Sem coluna tenant_id (isolamento fisico por banco).
-- Prefixo hm_ para conviver com tabelas de outros sistemas no mesmo banco.

IF OBJECT_ID(N'hm_users', N'U') IS NULL
BEGIN
    CREATE TABLE hm_users (
        id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name              NVARCHAR(200) NOT NULL,
        email             NVARCHAR(255) NOT NULL,
        job_title         NVARCHAR(200) NULL,
        is_owner          BIT NOT NULL CONSTRAINT df_hm_users_owner DEFAULT (0),
        is_active         BIT NOT NULL CONSTRAINT df_hm_users_active DEFAULT (1),
        password          NVARCHAR(255) NOT NULL,
        email_verified_at DATETIME2 NULL,
        created_at        DATETIME2 NOT NULL CONSTRAINT df_hm_users_created DEFAULT (SYSUTCDATETIME()),
        updated_at        DATETIME2 NOT NULL CONSTRAINT df_hm_users_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT uq_hm_users_email UNIQUE (email)
    );
END
GO

IF OBJECT_ID(N'hm_permissions', N'U') IS NULL
BEGIN
    CREATE TABLE hm_permissions (
        id   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(150) NOT NULL,
        CONSTRAINT uq_hm_permissions_name UNIQUE (name)
    );
END
GO

IF OBJECT_ID(N'hm_roles', N'U') IS NULL
BEGIN
    CREATE TABLE hm_roles (
        id        BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name      NVARCHAR(150) NOT NULL,
        label     NVARCHAR(150) NULL,
        is_system BIT NOT NULL CONSTRAINT df_hm_roles_system DEFAULT (0),
        CONSTRAINT uq_hm_roles_name UNIQUE (name)
    );
END
GO

IF OBJECT_ID(N'hm_role_permissions', N'U') IS NULL
BEGIN
    CREATE TABLE hm_role_permissions (
        role_id       BIGINT NOT NULL,
        permission_id BIGINT NOT NULL,
        CONSTRAINT pk_hm_role_permissions PRIMARY KEY (role_id, permission_id),
        CONSTRAINT fk_hm_rp_role FOREIGN KEY (role_id) REFERENCES hm_roles (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_rp_perm FOREIGN KEY (permission_id) REFERENCES hm_permissions (id) ON DELETE CASCADE
    );
END
GO

IF OBJECT_ID(N'hm_user_roles', N'U') IS NULL
BEGIN
    CREATE TABLE hm_user_roles (
        user_id BIGINT NOT NULL,
        role_id BIGINT NOT NULL,
        CONSTRAINT pk_hm_user_roles PRIMARY KEY (user_id, role_id),
        CONSTRAINT fk_hm_ur_user FOREIGN KEY (user_id) REFERENCES hm_users (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_ur_role FOREIGN KEY (role_id) REFERENCES hm_roles (id) ON DELETE CASCADE
    );
END
GO
