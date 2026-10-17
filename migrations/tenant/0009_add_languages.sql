-- Contexto: banco do tenant (ex.: harmonis_acme)
-- Internacionalizacao: idioma preferido do usuario e idiomas aos quais um formulario esta
-- vinculado (lista separada por virgula: pt-BR, en, es). Formularios existentes ficam
-- disponiveis em todos os idiomas.
IF COL_LENGTH(N'hm_users', N'language') IS NULL
BEGIN
    ALTER TABLE hm_users ADD language NVARCHAR(10) NOT NULL
        CONSTRAINT df_hm_users_language DEFAULT ('pt-BR');
END
GO
IF COL_LENGTH(N'hm_forms', N'languages') IS NULL
BEGIN
    ALTER TABLE hm_forms ADD languages NVARCHAR(40) NOT NULL
        CONSTRAINT df_hm_forms_languages DEFAULT ('pt-BR,en,es');
END
GO
