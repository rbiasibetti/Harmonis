-- Executar na instancia (contexto: master).
-- Cria o banco de catalogo e o banco do tenant de demonstracao.
-- Idempotente. Em producao, cada cliente tera seu proprio banco harmonis_<slug>
-- (ou um banco existente de outro sistema, onde as tabelas hm_* convivem com as demais).

IF DB_ID('harmonis_catalog') IS NULL
    CREATE DATABASE harmonis_catalog;
GO

IF DB_ID('harmonis_acme') IS NULL
    CREATE DATABASE harmonis_acme;
GO
