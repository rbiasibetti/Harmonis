# Harmonis — Especificação de Handoff (migração para Spring Boot + Angular)

> Documento de transferência de conhecimento. A implementação **Laravel + Livewire + MySQL** neste
> repositório é a **referência executável** (regras já validadas por ~84 testes). Este guia extrai o
> **domínio, as regras de negócio e os algoritmos** (que devem ser portados fielmente) e separa o que
> **muda** na nova stack: **Spring Boot (REST) + Angular (SPA)**, **SQL Server ≥ 2019**, e
> **multi-tenancy com um banco por cliente** (isolamento físico).

---

## 0. Como transferir o conhecimento (estratégia)

Você transfere **três coisas**, nesta ordem de importância:

1. **As regras e algoritmos** (este documento, seções 4–6). São independentes de framework e caros de
   redescobrir — principalmente o **motor de workflow**. Porte-os 1:1, cobrindo com testes equivalentes.
2. **A implementação de referência** (este repositório Git). Quando houver dúvida de comportamento,
   leia o arquivo-fonte apontado aqui e/ou rode o teste correspondente em `tests/Feature`.
3. **A intenção de produto / UX** (seções 1–2). O que cada módulo resolve e as decisões de experiência.

O que **NÃO** transferir: infraestrutura do Laravel (Livewire, Blade, Eloquent, spatie, middleware).
Isso é reimplementado com as ferramentas do Spring/Angular (seção 7).

Arquivos de referência rápidos no repo:
- `app/Workflow/Flow.php` e `app/Workflow/ApprovalService.php` — **motor de aprovação** (o núcleo).
- `config/acl.php` — permissões e papéis de sistema (fonte única).
- `config/forms.php` + `app/Support/FormSchema.php` — tipos de campo e validação.
- `database/migrations/*` — esquema completo.
- `tests/Feature/*Test.php` — especificação executável de cada regra.

---

## 1. Produto

**Harmonis** é uma plataforma SaaS leve que replica o núcleo do TOTVS Fluig (BPM/workflow, formulários,
ECM/GED, portal, identidade), corrigindo dores do Fluig (pesado, difícil de desenvolver, UX datada,
mobile fraco). Princípios: **leve, intuitiva, responsiva, tema claro/escuro, pt-BR**.

### Módulos entregues (escopo a reproduzir)
| Módulo | Função |
|---|---|
| **Identidade & Tenancy** | Cadastro de empresa (tenant) + usuário dono; login/logout. |
| **Usuários** | CRUD de usuários, ativar/desativar, atribuição de papel. |
| **Papéis & Permissões (RBAC)** | Papéis de sistema (admin/gestor/colaborador) + **papéis personalizados por tenant**; matriz de permissões; dono tem tudo. |
| **Formulários** | Construtor visual (8 tipos de campo), publicação, preenchimento com validação dinâmica, listagem de respostas, **export CSV**. |
| **Workflow / Processos** | Fluxo de aprovação por formulário: etapas sequenciais, **aprovadores paralelos** (todos/um), **etapas condicionais**, **gateways** (ramificação exclusiva), caixa de aprovações, timeline. **Modelador visual** (drag-and-drop, zoom, mini-mapa). |
| **Documentos (ECM/GED)** | Pastas hierárquicas, upload com **versionamento**, download seguro, **metadados** e **busca**. |
| **Portal / Páginas** | CMS interno em **Markdown** (HTML sanitizado), publicação, portal de leitura. |
| **Dashboard** | Métricas reais do workspace (aprovações pendentes, processos, formulários, documentos, etc.). |
| **Notificações** | Notificações in-app (aprovação solicitada / processo concluído). |

---

## 2. Decisões de arquitetura — o que MUDA na nova stack

| Aspecto | Laravel (atual) | Spring Boot + Angular (novo) |
|---|---|---|
| **Multi-tenancy** | **1 banco**, coluna `tenant_id` + escopo global + middleware. | **1 banco por tenant** (isolamento físico). **Remover `tenant_id` de todas as tabelas de negócio.** Resolver o datasource por tenant a cada request. |
| **Catálogo de tenants** | Tabela `tenants` no mesmo banco. | **Banco mestre (catalog)** só com o registro de tenants e o roteamento de login. |
| **Banco** | MySQL/MariaDB. | **SQL Server ≥ 2019** (ver seção 8). |
| **RBAC multi-tenant** | spatie/permission + *teams* (complexo) para papéis por tenant. | **Trivial**: cada tenant tem seu próprio banco; papéis/permissões são linhas locais. O `tenant_id` em roles/pivôs **deixa de existir**. |
| **Front/Back** | Monólito Livewire (server-rendered). | **API REST (Spring)** + **SPA (Angular)**. Precisa de autenticação por token (JWT) ou sessão, DTOs, CORS. |
| **Camada de view** | Blade/Livewire + Alpine + Tailwind. | Angular (componentes) — **Tailwind pode ser mantido**. |
| **ORM/Migrations** | Eloquent + migrations PHP. | JPA/Hibernate + **Flyway** (ou Liquibase). |
| **Jobs/Notif.** | DB notifications. | Tabela `notifications` própria + eventos de domínio (opcional: WebSocket/STOMP para push). |

### 2.1 Multi-tenancy banco-por-tenant (padrão recomendado em Spring)

- **Catalog DB** (`harmonis_catalog`): tabela `tenants` (id, nome, slug/subdomínio, connection info/nome do
  banco, plano, ativo). Opcional: um **diretório de login** `tenant_users` (email → tenant) **se** o login
  não for por subdomínio.
- **Tenant DB** (`harmonis_<slug>`): todas as tabelas de negócio (users, roles, forms, documents, …) **sem
  `tenant_id`**.
- **Resolução do tenant por request**: de preferência pelo **subdomínio** (`acme.harmonis.app`) ou por um
  **claim no JWT** (`tenant`). Um `Filter`/`Interceptor` coloca o id do tenant num `ThreadLocal`
  (`TenantContext`).
- **Hibernate multi-tenancy (estratégia DATABASE)**:
  - `CurrentTenantIdentifierResolver` lê o `TenantContext`.
  - `MultiTenantConnectionProvider` devolve a `DataSource` do tenant (mapa `Map<String, DataSource>` com
    HikariCP, criado sob demanda a partir do catalog). Alternativa mais simples: `AbstractRoutingDataSource`.
- **Provisionamento de tenant** (ao cadastrar empresa): (1) cria registro no catalog; (2) cria o banco
  `harmonis_<slug>`; (3) roda **Flyway** nesse banco; (4) faz o **seed** de permissões + papéis de sistema +
  usuário dono. Rodar isso numa transação lógica / com rollback do banco criado em caso de falha.
- **Startup**: migrar (Flyway) o catalog e **todos os bancos de tenant** conhecidos.

> Benefício: cross-tenant isolation é físico (não há risco de vazamento por escopo esquecido), e **papéis
> personalizados por tenant** saem de graça (não precisa do truque de *teams*).

### 2.2 Autenticação (Spring Security)
- Login retorna **JWT** contendo `sub` (userId), `tenant` (slug) e os papéis; a SPA guarda e envia em
  `Authorization: Bearer`.
- Se o acesso for por **subdomínio**, o tenant vem do host (não precisa estar no token para rotear, mas é
  bom ter no token para validação).
- `@PreAuthorize("hasAuthority('forms.view')")` nos endpoints (as *authorities* são as permissões).
- **Owner bypass**: usuário com `is_owner = true` tem todas as permissões — implemente um
  `PermissionEvaluator`/`GrantedAuthoritiesMapper` que concede tudo ao owner (equivalente ao `Gate::before`).

---

## 3. Mapeamento Laravel → Spring Boot + Angular

| Laravel | Spring Boot / Angular |
|---|---|
| Eloquent Model | JPA `@Entity` + `JpaRepository` |
| Migration | Flyway `V__*.sql` (SQL Server) |
| `config/acl.php`, `config/forms.php` | Constantes/enum Java (`AclCatalog`, `FieldType`) — ver apêndices |
| Middleware `SetCurrentTenant` | `OncePerRequestFilter` que popula `TenantContext` |
| Global scope `BelongsToTenant` | **Desnecessário** (isolamento por banco) |
| spatie/permission | Tabelas próprias `permissions`, `roles`, `role_permissions`, `user_roles` + Spring Security |
| `Gate::before` (owner) | `PermissionEvaluator` que libera tudo para owner |
| Livewire component | Componente Angular + endpoint(s) REST |
| Blade view | Template Angular (Tailwind mantido) |
| `Notification` (database) | Tabela `notifications` + `@EventListener` de eventos de domínio |
| `Str::markdown()` (CommonMark, HTML stripado) | **flexmark-java** ou **commonmark-java** + **sanitização** (OWASP Java HTML Sanitizer / jsoup) |
| `response()->streamDownload` (CSV) | `StreamingResponseBody` / `ResponseEntity<byte[]>` |
| Storage (disco privado) | `Resource`/filesystem ou S3; download por endpoint autenticado |
| ULID (`Str::ulid`) | `java.util.UUID` ou lib ULID (ids de campos/nós de fluxo) |

---

## 4. Modelo de dados (por módulo) — já SEM `tenant_id`

> Todas as tabelas abaixo vivem no **banco do tenant**, exceto `tenants` (catalog). Tipos em SQL Server.
> `id BIGINT IDENTITY PRIMARY KEY`; timestamps `created_at/updated_at DATETIME2`; booleans `BIT`;
> "JSON" = `NVARCHAR(MAX)` com `CHECK (ISJSON(col)=1)` (ver seção 8).

### 4.1 Catalog (banco mestre)
**tenants**: `id`, `name NVARCHAR`, `slug NVARCHAR UNIQUE` (= subdomínio), `db_name NVARCHAR`,
`plan NVARCHAR default 'free'`, `is_active BIT default 1`, timestamps.
*(Opcional, se login não for por subdomínio)* **tenant_directory**: `email UNIQUE`, `tenant_id` → roteia o login.

### 4.2 Identidade (tenant DB)
**users**: `id`, `name`, `email UNIQUE`, `job_title NVARCHAR NULL`, `is_owner BIT default 0`,
`is_active BIT default 1`, `password NVARCHAR` (BCrypt), `email_verified_at DATETIME2 NULL`, timestamps.

### 4.3 RBAC (tenant DB) — substitui spatie
- **permissions**: `id`, `name UNIQUE` (ex.: `forms.view`).
- **roles**: `id`, `name`, `label NVARCHAR NULL`, `is_system BIT default 0`. (`UNIQUE(name)` dentro do banco.)
- **role_permissions**: `role_id`, `permission_id` (PK composta).
- **user_roles**: `user_id`, `role_id` (PK composta). *(No modelo atual cada usuário tem 1 papel, mas a
  relação é N:N — mantenha N:N.)*

Papéis de sistema (`is_system=1`): `admin`, `manager`, `member`. **Papéis personalizados**: `is_system=0`,
criados pelo tenant (editáveis/removíveis). Ver permissões exatas no **Apêndice A**.

### 4.4 Formulários (tenant DB)
**forms**: `id`, `name`, `slug` (UNIQUE no banco), `description NVARCHAR NULL`, `schema JSON NULL`
(`{"fields":[...]}`), `approval_flow JSON NULL` (árvore de nós — seção 6), `status NVARCHAR default 'draft'`
(`draft|published`), `created_by` → users NULL, timestamps.

**form_submissions**: `id`, `form_id` → forms, `data JSON` (`{ "<fieldKey>": valor }`),
`submitted_by` → users NULL, timestamps.

### 4.5 Workflow (tenant DB)
**process_instances**: `id`, `form_id` → forms, `submission_id` → form_submissions,
`status NVARCHAR default 'pending'` (`pending|approved|rejected`), `current_step INT default 0`,
`created_by` → users NULL, timestamps.

**process_tasks**: `id`, `process_instance_id` → process_instances, `step_index INT`,
`step_name NVARCHAR`, `approver_type NVARCHAR default 'role'` (`role|user`), `role NVARCHAR NULL`,
`assigned_user_id` → users NULL, `status NVARCHAR default 'pending'` (`pending|approved|rejected|skipped`),
`decided_by` → users NULL, `comment NVARCHAR(MAX) NULL`, `decided_at DATETIME2 NULL`, timestamps.

### 4.6 Documentos / ECM (tenant DB)
**folders**: `id`, `parent_id` → folders NULL (cascade), `name`, `created_by` → users NULL, timestamps.

**documents**: `id`, `folder_id` → folders NULL, `name`, `description NVARCHAR NULL`,
`metadata JSON NULL` (pares chave/valor), `current_version INT default 1`, `created_by` → users NULL, timestamps.

**document_versions**: `id`, `document_id` → documents, `version INT`, `disk NVARCHAR default 'local'`,
`path NVARCHAR`, `original_name NVARCHAR`, `mime_type NVARCHAR NULL`, `size BIGINT default 0`,
`uploaded_by` → users NULL, timestamps. `UNIQUE(document_id, version)`.

### 4.7 Portal (tenant DB)
**pages**: `id`, `title`, `slug` (UNIQUE no banco), `excerpt NVARCHAR NULL`, `content NVARCHAR(MAX) NULL`
(Markdown), `status NVARCHAR default 'draft'` (`draft|published`), `published_at DATETIME2 NULL`,
`created_by` → users NULL, timestamps.

### 4.8 Notificações (tenant DB)
**notifications**: `id UNIQUEIDENTIFIER/CHAR(36)`, `type NVARCHAR`, `notifiable_type NVARCHAR`,
`notifiable_id BIGINT` (quem recebe; na prática sempre user), `data JSON`, `read_at DATETIME2 NULL`, timestamps.
`data` = `{ type, title, message, url, instance_id }`.

---

## 5. Regras de negócio (portar fielmente)

### 5.1 RBAC
- Permissões são strings `recurso.acao` (Apêndice A). Papéis agregam permissões.
- `admin` = **todas** as permissões. `manager`/`member` = subconjuntos (Apêndice A).
- **Owner** (`is_owner`) ignora checagem e tem tudo (bypass global).
- Papéis personalizados: nome único por tenant, **não pode colidir** com nomes de sistema
  (`admin/manager/member`); recebem qualquer subconjunto de permissões. (No banco-por-tenant isso é só
  CRUD local.)

### 5.2 Construtor de formulários
- **Tipos de campo** (8): `text, textarea, number, email, date, select, radio, checkbox`.
  `select`/`radio` têm `options[]`; os demais não. (Apêndice B.)
- **Definição de campo**: `{ id, key:"campo_xxxxxx", type, label, placeholder, help, required(bool), options[] }`.
  `key` é o identificador estável usado em `submissions.data`.
- **Validação no submit** (mapear do `FormSchema::validationRules`):
  - `required` → obrigatório; senão opcional.
  - `number`→numérico; `email`→email; `date`→data; `checkbox`→booleano (se required, deve ser "aceito");
    `select`/`radio`→valor ∈ `options`; demais→string.
- Publicação (`status=published`) é pré-requisito para preencher. `slug` único por tenant.

### 5.3 Documentos / versionamento
- Upload cria `document` (v1) + `document_version`. **Nova versão** incrementa `current_version` e
  **preserva** as anteriores (histórico). Download sempre via **endpoint autenticado** (arquivos nunca
  públicos). Excluir documento remove **todos os arquivos físicos** das versões. Excluir pasta remove
  recursivamente o conteúdo.
- **Busca** (tenant-wide): casa em `name`, `description`, valores de `metadata` e `original_name` da versão.
- **Metadados**: pares chave/valor livres, exibidos e pesquisáveis.

### 5.4 Portal / páginas
- Conteúdo em **Markdown**, renderizado para HTML **com HTML inseguro removido** (anti-XSS) — use
  commonmark-java/flexmark + sanitizador. Rascunho só é visível para quem pode editar; portal lista só
  `published`. `slug` único por tenant.

### 5.5 Export CSV (respostas)
- Colunas: `#`, `Enviado por`, `Data` + **uma coluna por campo** do formulário. `checkbox`→`Sim/Não`;
  valores de lista unidos por vírgula. Separador **`;`** e **BOM UTF-8** (abre certo no Excel pt-BR).

### 5.6 Dashboard
- Cards (gated por permissão): aprovações pendentes do usuário, processos ativos, formulários publicados,
  documentos, páginas publicadas, usuários ativos (mostrar os primeiros conforme permissão). Listas:
  aprovações pendentes, minhas solicitações, respostas recentes.

### 5.7 Notificações
- **ApprovalRequested**: enviada aos aprovadores de uma tarefa recém-criada (excluindo o solicitante).
- **ProcessCompleted**: enviada ao solicitante na decisão final (aprovado/reprovado).
- Persistidas em `notifications`; UI com badge de não-lidas, marcar como lida, abrir → navega para a URL.

---

## 6. Motor de Workflow (o núcleo — portar 1:1)

Referência: `app/Workflow/Flow.php` (`Flow.*`) e `app/Workflow/ApprovalService.php`.
Testes: `tests/Feature/WorkflowTest.php`, `tests/Feature/FlowModelerTest.php`.

### 6.1 Formato do `approval_flow` (árvore de nós)
É um **array ordenado** de nós. Dois tipos:

**Etapa (step):**
```json
{
  "id": "ulid",
  "name": "Aprovação do gestor",
  "mode": "all",                 // "all" = todos aprovam | "any" = basta um
  "approvers": [
    { "approver_type": "role", "role": "manager", "user_id": null },
    { "approver_type": "user", "role": null, "user_id": 42 }
  ],
  "condition": { "field": "valor", "operator": ">", "value": "1000" }  // ou null
}
```

**Gateway (decisão exclusiva):**
```json
{
  "id": "ulid",
  "type": "gateway",
  "name": "Valor alto?",
  "condition": { "field": "valor", "operator": ">", "value": "1000" },
  "then": [ /* nós (etapas/gateways) se verdadeiro */ ],
  "else": [ /* nós se falso */ ]
}
```
`isGateway(node) = node.type == "gateway"`. Compatibilidade: nó legado sem `approvers` (só
`approver_type/role/user_id` no topo) → normalizar para `approvers:[{...}]`, `mode:"all"`.

### 6.2 Operadores de condição (`Flow.OPERATORS`)
`==` (igual), `!=` (diferente), `>`, `>=`, `<`, `<=`, `contains` (contém), `empty` (vazio),
`not_empty` (preenchido).

**Avaliação** `conditionPasses(condition, data)`:
- `condition` nula ou sem `field` → **true** (sempre executa).
- `actual = data[field]`; se `actual` é booleano, vira `"1"`/`"0"`.
- `empty` → actual é null/""/[]; `not_empty` → o contrário.
- `==`/`!=` → **igualdade frouxa**: se ambos numéricos, compara número; senão compara string
  `lower(trim(...))`.
- `>,>=,<,<=` → compara **numericamente** se ambos forem numéricos; senão **false**.
- `contains` → substring case-insensitive.

### 6.3 Resolução do plano (gateways → lista linear)
`resolvePlan(nodes, data)` (recursivo) achata a árvore em **lista ordenada de etapas**:
```
para cada nó em nodes:
  se gateway:
     ramo = conditionPasses(nó.condition, data) ? nó.then : nó.else
     plano += resolvePlan(ramo, data)
  senão:
     plano += [nó]   // é uma etapa
```
As condições dependem só dos **dados imutáveis** da submissão ⇒ o plano é **determinístico** e pode ser
recalculado a cada decisão (os índices permanecem estáveis). **Toda a execução roda sobre o plano**, então
o resto do motor continua "linear".

### 6.4 Execução
Estado: `process_instances.current_step` (índice no plano), `process_tasks.step_index` (idem). Uma etapa
com N aprovadores gera **N tasks** no mesmo `step_index`.

**start(form, submission, creator):**
1. cria instância (`status=pending`, `current_step=0`).
2. `openStepFrom(instance, 0)`.

**openStepFrom(instance, fromIndex):**
```
plan = resolvePlan(form.approval_flow, submission.data)
para i de fromIndex até fim do plan:
  step = normalize(plan[i])
  se NÃO conditionPasses(step.condition, data): continua   // etapa condicional pulada
  instance.current_step = i
  para cada approver em step.approvers: cria task(step_index=i, ...); notifica aprovadores
  return
// nenhuma etapa aplicável:
instance.status = approved; notifica solicitante(aprovado)
```
`createTask`: `{ step_index, step_name=step.name, approver_type, role (se role), assigned_user_id (se user), status=pending }`.

**decide(task, approved, comment, user):**
```
se task.status != pending: return
task.status = approved ? "approved" : "rejected"; task.decided_by/comment/decided_at
index = task.step_index; plan = resolvePlan(...); step = normalize(plan[index]); mode = step.mode

se mode == "any":
   se approved: skipRemaining(index); openStepFrom(index+1)
   senão se (pendentes_no_index == 0 E aprovados_no_index == 0): reject(instance)
senão (mode == "all"):
   se NÃO approved: skipRemaining(index); reject(instance)
   senão se pendentes_no_index == 0: openStepFrom(index+1)
```
- `skipRemaining(index)`: tasks `pending` naquele `step_index` → `status="skipped"`.
- `reject(instance)`: `status="rejected"` + notifica solicitante(reprovado).
- Avançar para além do último índice ⇒ `status="approved"` + notifica solicitante(aprovado) (dentro de
  `openStepFrom`).

**canDecide(task, user):** `task.pending AND (user.is_owner OR (approver_type=="user" ?
assigned_user_id==user.id : user.hasRole(task.role)))`.

**approversFor(task):** `user` → aquele usuário; `role` → usuários **ativos** com aquele papel.

**pendingApprovals(user):** tasks `pending` de instâncias `pending` onde, se não for owner,
`role ∈ papéis_do_usuário OR assigned_user_id == user.id`. (Owner vê todas.) Usado no contador do menu e no
dashboard.

### 6.5 Modelador visual (Angular)
Edita o **mesmo** `approval_flow`. Reproduzir:
- Canvas: `Início → nós → Concluído`; nota "qualquer reprovação encerra".
- Nó etapa: chips de aprovador, badge `Paralela · todos/um` (quando >1), rótulo de condição.
- Nó gateway: losango + dois ramos (**Se verdadeiro / Se falso**) que convergem.
- Inserir (etapa **ou** decisão) em qualquer posição; **drag-and-drop** para reordenar dentro da mesma
  lista (Angular CDK `DragDrop`); **zoom** (transform scale) e **mini-mapa** (outline clicável).
- Editor lateral conforme o tipo (etapa: nome/aprovadores/modo/condição; gateway: nome/condição + inserir
  etapa em cada ramo).
- Endereçamento de nó por **caminho**: `"i"` (topo), `"i.then.j"`, `"i.else.j"` (útil para a API de edição).
- Validação antes de salvar: etapa precisa de nome e aprovadores válidos; gateway precisa de nome e **campo
  de condição**; `field` referenciado deve existir no `schema` do formulário.

---

## 7. Ordem de implementação sugerida (milestones)

Espelha a evolução validada neste repo (cada marco com testes):
1. **M0 — Fundação:** catalog + provisionamento de tenant (criar DB + Flyway + seed), auth JWT, layout
   Angular (shell, tema claro/escuro), CORS.
2. **M1 — Identidade & RBAC:** users CRUD, roles/permissions (sistema + personalizados), owner bypass,
   `@PreAuthorize`.
3. **M2 — Formulários:** construtor, schema JSON, publicação, preenchimento + validação, respostas, CSV.
4. **M3 — Workflow:** entidades de processo, **motor** (seção 6) com testes de paralelo/condicional/gateway,
   caixa de aprovações, timeline, notificações.
5. **M4 — ECM/GED:** pastas, upload/versionamento, download seguro, metadados, busca.
6. **M5 — Portal:** páginas Markdown (sanitizado), portal.
7. **M6 — Dashboard + Modelador visual** (drag-and-drop, zoom, mini-mapa).

---

## 8. SQL Server ≥ 2019 — cheat-sheet de portabilidade

| MySQL (atual) | SQL Server 2019+ |
|---|---|
| `BIGINT AUTO_INCREMENT` | `BIGINT IDENTITY(1,1)` |
| `BOOLEAN`/`TINYINT(1)` | `BIT` |
| `TIMESTAMP`/`DATETIME` | `DATETIME2` (use `SYSUTCDATETIME()`); evite `DATETIME` |
| `VARCHAR`/`TEXT` | `NVARCHAR(n)` / `NVARCHAR(MAX)` (Unicode) |
| `JSON` | `NVARCHAR(MAX)` + `CONSTRAINT CHECK (ISJSON(col)=1)` |
| `JSON_EXTRACT`/`->>` | `JSON_VALUE(col,'$.campo')`, `OPENJSON(col)` |
| `uuid` | `UNIQUEIDENTIFIER` (ou `CHAR(36)`) |
| `LIMIT x OFFSET y` | `OFFSET y ROWS FETCH NEXT x ROWS ONLY` (requer `ORDER BY`) |
| busca case-insensitive por padrão | depende do **collation** (ex.: `..._CI_AS` = case-insensitive) |

Notas:
- **JSON é texto** no SQL Server: para filtrar por metadados/condições, use `JSON_VALUE`/`OPENJSON` ou
  resolva em memória (o motor de workflow já avalia em memória — mantenha assim).
- **Busca de documentos**: `WHERE name LIKE @q OR description LIKE @q OR metadata LIKE @q OR EXISTS(...)`;
  com collation `_CI_`, `LIKE` já é case-insensitive (dispensa `LOWER()`).
- Hibernate dialect: `SQLServerDialect`. Driver: `mssql-jdbc`. Flyway suporta SQL Server nativamente.
- IDs de **campos** e **nós de fluxo** são ULIDs/strings geradas na aplicação (não dependem do banco).

---

## Apêndice A — Permissões e papéis de sistema (fonte: `config/acl.php`)

**Permissões (agrupadas):**
- **Usuários:** `users.view`, `users.create`, `users.update`, `users.delete`
- **Papéis & permissões:** `roles.view`, `roles.create`, `roles.update`, `roles.delete`
- **Formulários:** `forms.view`, `forms.create`, `forms.update`, `forms.delete`, `forms.submissions`
- **Processos:** `processes.view`
- **Documentos:** `documents.view`, `documents.create`, `documents.update`, `documents.delete`
- **Portal:** `pages.view`, `pages.create`, `pages.update`, `pages.delete`

**Papéis de sistema:**
- **admin** (rótulo *Administrador*): **todas** as permissões.
- **manager** (*Gestor*): `users.view, users.create, users.update, roles.view, forms.view, forms.create,
  forms.update, forms.submissions, processes.view, documents.{view,create,update,delete},
  pages.{view,create,update,delete}`.
- **member** (*Colaborador*): `forms.view, processes.view, documents.view, documents.create, pages.view`.
- **Owner** (`is_owner=1`): bypass — tem tudo.

## Apêndice B — Tipos de campo (fonte: `config/forms.php`)
`text` (Texto curto), `textarea` (Texto longo), `number` (Número), `email` (E-mail), `date` (Data),
`select` (Lista suspensa, **options**), `radio` (Múltipla escolha, **options**), `checkbox` (Confirmação sim/não).

## Apêndice C — Enums de status
- `forms.status`: `draft | published`
- `pages.status`: `draft | published`
- `process_instances.status`: `pending | approved | rejected`
- `process_tasks.status`: `pending | approved | rejected | skipped`
- `process_tasks.approver_type`: `role | user`
- step `mode`: `all | any`

## Apêndice D — Aprendizados/armadilhas relevantes
- **Workflow**: o segredo de suportar gateway + paralelo + condicional sem complicar o executor é
  **resolver a árvore num plano linear** por submissão (determinístico) e rodar o executor linear sobre ele.
- **Markdown**: sempre **sanitizar** o HTML gerado (strip de tags perigosas) antes de exibir.
- **CSV pt-BR**: separador `;` + **BOM UTF-8**.
- **Downloads**: nunca servir arquivos de documento por URL pública — sempre endpoint autenticado e
  autorizado.
- **DB-por-tenant simplifica muito o RBAC**: o que no Laravel exigiu spatie *teams* (papéis por tenant) vira
  CRUD local; não há `tenant_id` em lugar nenhum das tabelas de negócio.
- **Não** confie em nome para inferir papel/permissão; sempre cheque a permissão concreta.
