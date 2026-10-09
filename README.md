# Harmonis — Spring Boot + Angular + SQL Server

Plataforma SaaS leve que replica o núcleo do TOTVS Fluig (workflow/BPM, formulários, ECM/GED,
portal, identidade). Esta é a reimplementação em **Spring Boot (API REST) + Angular (SPA)** sobre
**SQL Server ≥ 2019**, com **um banco por tenant** (isolamento físico).

> Especificação de domínio e regras de negócio: [`docs/HANDOFF-SPRING-ANGULAR.md`](docs/HANDOFF-SPRING-ANGULAR.md).
> A implementação de referência (Laravel) está no repositório anterior / bundle `harmonis-referencia.bundle`.

## Decisões desta implementação

- **Banco por tenant.** Catálogo mestre `harmonis_catalog` (tabela `hm_tenants`) roteia o login
  para o banco de cada cliente (`harmonis_<slug>`). Nenhuma tabela de negócio tem `tenant_id`.
- **Prefixo `hm_` em todas as tabelas.** As tabelas do Harmonis podem conviver com tabelas de
  outros sistemas no mesmo banco de um cliente — o prefixo evita colisões.
- **Sem Flyway.** O schema é gerido manualmente por scripts SQL versionados em [`migrations/`](migrations/),
  aplicados conforme o desenvolvimento avança. A aplicação roda com `spring.jpa.hibernate.ddl-auto=none`.
- **Autenticação JWT.** O token carrega `tenant`, `owner`, papéis e permissões (authorities). Um
  filtro resolve o tenant por request e popula o datasource de roteamento.

## Estrutura

```
backend/      API REST Spring Boot (Java 21, Maven)
frontend/     SPA Angular (a construir)
migrations/   Scripts SQL (SQL Server) aplicados manualmente
  000_create_databases.sql      cria harmonis_catalog e harmonis_acme (contexto: master)
  catalog/0001_create_tenants.sql   hm_tenants + tenant de demo "acme"
  tenant/0001_create_identity.sql   hm_users, hm_roles, hm_permissions, pivôs
tools/        Utilitários de desenvolvimento (ex.: MigrationRunner)
docs/         Especificação de handoff
```

## Pré-requisitos

- **JDK 21** (LTS). Neste ambiente: `~/.local/jdks/jdk-21.0.12.1+1`.
- **SQL Server ≥ 2019** local, acessível em `localhost:1433`.
- Node 20 + Angular CLI (frontend).

## Configuração do banco

Credenciais lidas de `backend/src/main/resources/application.yml` (sobrescrevíveis por variáveis de
ambiente `HARMONIS_DB_*`). Padrão de desenvolvimento: usuário `sa`.

### Aplicar migrations (sem Flyway)

Há um runner Java simples que executa scripts `.sql` separando batches por `GO`:

```bash
JDK=~/.local/jdks/jdk-21.0.12.1+1/bin/java
JAR=~/.m2/repository/com/microsoft/sqlserver/mssql-jdbc/12.10.1.jre11/mssql-jdbc-12.10.1.jre11.jar
OPTS=";encrypt=true;trustServerCertificate=true"

# 1) bancos (contexto master)
$JDK -cp "$JAR" tools/MigrationRunner.java "jdbc:sqlserver://localhost:1433;databaseName=master$OPTS" sa '<senha>' migrations/000_create_databases.sql
# 2) catálogo
$JDK -cp "$JAR" tools/MigrationRunner.java "jdbc:sqlserver://localhost:1433;databaseName=harmonis_catalog$OPTS" sa '<senha>' migrations/catalog/0001_create_tenants.sql
# 3) tenant de demo
$JDK -cp "$JAR" tools/MigrationRunner.java "jdbc:sqlserver://localhost:1433;databaseName=harmonis_acme$OPTS" sa '<senha>' migrations/tenant/0001_create_identity.sql
```

## Rodar o backend

```bash
cd backend
./mvn.sh -DskipTests package
java -jar target/harmonis-backend.jar --harmonis.seed.tenant=acme
```

`--harmonis.seed.tenant=acme` semeia (idempotente) as permissões, os papéis de sistema
(admin/manager/member) e o usuário dono do tenant. Login de demonstração:
**admin@acme.test / password**.

### Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/health` | — | Saúde do serviço |
| POST | `/api/auth/login` | — | `{tenant,email,password}` → `{token,user}` |
| GET | `/api/me` | autenticado | Usuário autenticado (resolve o tenant pelo JWT) |
| GET | `/api/users` | `users.view` | Lista usuários (`?q=` busca nome/e-mail) |
| POST | `/api/users` | `users.create` | Cria usuário (retorna senha inicial gerada) |
| PUT | `/api/users/{id}` | `users.update` | Atualiza nome/cargo/papéis/ativo |
| DELETE | `/api/users/{id}` | `users.delete` | Remove usuário (owner/eu protegidos) |
| GET | `/api/roles` | `roles.view` | Lista papéis (sistema + personalizados) |
| GET | `/api/permissions` | `roles.view` | Catálogo de permissões agrupado |
| POST | `/api/roles` | `roles.create` | Cria papel personalizado |
| PUT | `/api/roles/{id}` | `roles.update` | Edita papel personalizado (sistema é imutável) |
| DELETE | `/api/roles/{id}` | `roles.delete` | Remove papel personalizado |
| GET | `/api/forms/field-types` | `forms.view` | Catálogo dos 8 tipos de campo |
| GET | `/api/forms` | `forms.view` | Lista formulários (com contagens) |
| GET | `/api/forms/{id}` | `forms.view` | Detalhe (schema de campos) |
| POST | `/api/forms` | `forms.create` | Cria formulário |
| PUT | `/api/forms/{id}` | `forms.update` | Atualiza formulário/campos/status |
| DELETE | `/api/forms/{id}` | `forms.delete` | Remove formulário (e respostas) |
| POST | `/api/forms/{id}/submissions` | `forms.view` | Envia resposta (valida; 422 por campo) |
| GET | `/api/forms/{id}/submissions` | `forms.submissions` | Lista respostas |
| GET | `/api/forms/{id}/submissions.csv` | `forms.submissions` | Export CSV (`;` + BOM) |

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"tenant":"acme","email":"admin@acme.test","password":"password"}'
```

## Testes

```bash
cd backend && ./mvn.sh test
```

## Roadmap (milestones — espelha a referência)

- [x] **M0** — Fundação: catálogo + roteamento por tenant, auth JWT, identidade/RBAC (seed), CORS.
- [x] **M1** — Usuários CRUD, papéis/permissões (sistema + personalizados), `@PreAuthorize`.
- [x] **M2** — Formulários (construtor, schema JSON, validação, respostas, CSV).
- [ ] **M3** — Workflow (motor de aprovação: paralelo/condicional/gateway, caixa, timeline, notificações).
- [ ] **M4** — ECM/GED (pastas, versionamento, download seguro, metadados, busca).
- [ ] **M5** — Portal (páginas Markdown sanitizado).
- [ ] **M6** — Dashboard + modelador visual (drag-and-drop, zoom, mini-mapa).
