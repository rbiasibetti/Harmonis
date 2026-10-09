-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Workflow de aprovacao + notificacoes. Prefixo hm_.

IF OBJECT_ID(N'hm_process_instances', N'U') IS NULL
BEGIN
    CREATE TABLE hm_process_instances (
        id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_id       BIGINT NOT NULL,
        submission_id BIGINT NOT NULL,
        status        NVARCHAR(20) NOT NULL CONSTRAINT df_hm_pi_status DEFAULT ('pending'),
        current_step  INT NOT NULL CONSTRAINT df_hm_pi_step DEFAULT (0),
        created_by    BIGINT NULL,
        created_at    DATETIME2 NOT NULL CONSTRAINT df_hm_pi_created DEFAULT (SYSUTCDATETIME()),
        updated_at    DATETIME2 NOT NULL CONSTRAINT df_hm_pi_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_pi_form FOREIGN KEY (form_id) REFERENCES hm_forms (id),
        CONSTRAINT fk_hm_pi_sub FOREIGN KEY (submission_id) REFERENCES hm_form_submissions (id),
        CONSTRAINT fk_hm_pi_creator FOREIGN KEY (created_by) REFERENCES hm_users (id)
    );
END
GO

IF OBJECT_ID(N'hm_process_tasks', N'U') IS NULL
BEGIN
    CREATE TABLE hm_process_tasks (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        process_instance_id  BIGINT NOT NULL,
        step_index           INT NOT NULL,
        step_name            NVARCHAR(200) NOT NULL,
        approver_type        NVARCHAR(20) NOT NULL CONSTRAINT df_hm_pt_atype DEFAULT ('role'),
        role                 NVARCHAR(150) NULL,
        assigned_user_id     BIGINT NULL,
        status               NVARCHAR(20) NOT NULL CONSTRAINT df_hm_pt_status DEFAULT ('pending'),
        decided_by           BIGINT NULL,
        comment              NVARCHAR(MAX) NULL,
        decided_at           DATETIME2 NULL,
        created_at           DATETIME2 NOT NULL CONSTRAINT df_hm_pt_created DEFAULT (SYSUTCDATETIME()),
        updated_at           DATETIME2 NOT NULL CONSTRAINT df_hm_pt_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_pt_instance FOREIGN KEY (process_instance_id) REFERENCES hm_process_instances (id) ON DELETE CASCADE,
        CONSTRAINT fk_hm_pt_assignee FOREIGN KEY (assigned_user_id) REFERENCES hm_users (id),
        CONSTRAINT fk_hm_pt_decider FOREIGN KEY (decided_by) REFERENCES hm_users (id)
    );
    CREATE INDEX ix_hm_pt_instance ON hm_process_tasks (process_instance_id);
    CREATE INDEX ix_hm_pt_status ON hm_process_tasks (status);
END
GO

IF OBJECT_ID(N'hm_notifications', N'U') IS NULL
BEGIN
    CREATE TABLE hm_notifications (
        id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id     BIGINT NOT NULL,
        type        NVARCHAR(100) NOT NULL,
        title       NVARCHAR(200) NOT NULL,
        message     NVARCHAR(500) NULL,
        url         NVARCHAR(300) NULL,
        instance_id BIGINT NULL,
        read_at     DATETIME2 NULL,
        created_at  DATETIME2 NOT NULL CONSTRAINT df_hm_notif_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT fk_hm_notif_user FOREIGN KEY (user_id) REFERENCES hm_users (id) ON DELETE CASCADE
    );
    CREATE INDEX ix_hm_notif_user ON hm_notifications (user_id, read_at);
END
GO
