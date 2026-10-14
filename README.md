<div align="center">

# Harmonis

**Plataforma SaaS multi-tenant para automação de processos de negócio, formulários dinâmicos, gestão de documentos e portal de conteúdo.**

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![SQL Server](https://img.shields.io/badge/SQL%20Server-2019%2B-CC2927?logo=microsoftsqlserver&logoColor=white)](https://www.microsoft.com/sql-server)
[![Build](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

</div>

---

## Índice

- [Sobre o projeto](#sobre-o-projeto)
- [Recursos](#recursos)
- [Como funciona (arquitetura)](#como-funciona-arquitetura)
- [Stack](#stack)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Começando](#começando)
  - [Pré-requisitos](#pré-requisitos)
  - [1. Clonar](#1-clonar)
  - [2. Banco de dados e migrations](#2-banco-de-dados-e-migrations)
  - [3. Backend](#3-backend)
  - [4. Frontend](#4-frontend)
- [Configuração](#configuração)
- [Implantação em produção](#implantação-em-produção)
- [Provisionar um novo cliente (tenant)](#provisionar-um-novo-cliente-tenant)
- [Referência da API](#referência-da-api)
- [Modelo de permissões](#modelo-de-permissões)
- [Testes](#testes)
- [Roadmap](#roadmap)
- [Contribuição](#contribuição)
- [Licença](#licença)

---

## Sobre o projeto

O **Harmonis** é uma plataforma corporativa que reúne, em um único produto, as ferramentas que uma
organização usa para operar seus processos internos:

- **Workflow / BPM** — desenhe fluxos de aprovação (sequenciais, paralelos e condicionais) e
  acompanhe cada solicitação do início ao fim.
- **Formulários dinâmicos** — crie formulários sem código, com validações avançadas, layout
  responsivo e campos de anexo.
- **ECM / GED** — gestão eletrônica de documentos com pastas, versionamento e download seguro.
- **Portal de conteúdo** — páginas internas publicadas em Markdown.
- **Identidade e RBAC** — usuários, papéis e permissões granulares por recurso.

É **multi-tenant com isolamento físico**: cada cliente tem o seu próprio banco de dados. Isso
simplifica a conformidade, o backup/restore por cliente e a convivência das tabelas do Harmonis com
as de outros sistemas no mesmo banco (todas as tabelas usam o prefixo `hm_`).

> A API REST (Spring Boot) e a SPA (Angular) são totalmente desacopladas e se comunicam via JSON
> autenticado por JWT.

---

## Recursos

### Identidade e controle de acesso
- Autenticação por **JWT**; o token carrega o tenant, os papéis e as permissões (authorities).
- **Papéis de sistema** (Administrador, Gestor, Colaborador) e **papéis personalizados**.
- Autorização por **permissão granular** em cada endpoint (`@PreAuthorize`).
- O **dono do tenant** recebe todas as permissões.

### Formulários
- Construtor visual com **11 tipos de campo**: texto curto/longo, número, e-mail, data, seleção
  única (combo ou radio), seleção múltipla (combo ou checkbox), confirmação (sim/não),
  **anexo único**, **anexo múltiplo** e **blocos/seções** de layout.
- **Validações** por campo: regex com mensagem, valores positivos/inteiros, mínimo/máximo,
  tamanho de texto, datas mínima/máxima e **comparação entre campos** (ex.: data fim > data início).
- **Exibição condicional** ("mostrar o campo X somente quando Y estiver preenchido").
- **Layout responsivo** em grid (largura inteira / metade / um terço).
- **Anexos** com tipos aceitos (`accept`) e tamanho máximo por arquivo configuráveis.
- **Segmentação por papel**: parte do formulário pode ser preenchida por um papel específico
  **durante o fluxo de aprovação** (o aprovador completa os campos que lhe cabem ao decidir).
- Exportação das respostas em **CSV**.

### Workflow / BPM
- Motor de aprovação que opera sobre uma árvore de etapas, com suporte a:
  - **Aprovadores por papel ou por usuário**; etapas com **múltiplos aprovadores** (modo *todos* ou *qualquer um*).
  - **Gateways condicionais** (ramos "se verdadeiro / se falso") avaliados a partir dos dados da resposta.
  - **Etapas condicionais** (executadas somente quando uma condição é satisfeita).
- **Modelador visual** com arrastar-e-soltar, zoom e mini-mapa.
- **Caixa de aprovações**, acompanhamento das próprias solicitações e **timeline** por processo.
- **Notificações** in-app (sino) para solicitações e conclusões.

### ECM / GED
- Pastas hierárquicas com navegação por *breadcrumb* e exclusão recursiva.
- **Versionamento** de documentos preservando o histórico.
- Metadados chave/valor e **busca** por nome, descrição, metadados e nome do arquivo.
- **Download sempre autenticado** (os arquivos nunca são servidos diretamente do disco).

### Portal
- Páginas em **Markdown** convertidas para HTML **sanitizado** (proteção contra XSS).
- Fluxo de rascunho → publicação com pré-visualização.

### Dashboard
- Métricas e listas em tempo real, com cada cartão **condicionado à permissão** do usuário.

---

## Como funciona (arquitetura)

```
┌──────────────┐   HTTPS / JSON (JWT)   ┌─────────────────────────┐
│  Angular SPA │ ─────────────────────► │   Spring Boot REST API  │
│  (frontend)  │ ◄───────────────────── │       (backend)         │
└──────────────┘                        └───────────┬─────────────┘
                                                     │ RoutingDataSource
                            ┌────────────────────────┼────────────────────────┐
                            ▼                         ▼                        ▼
                   ┌─────────────────┐      ┌──────────────────┐     ┌──────────────────┐
                   │ harmonis_catalog │      │  harmonis_acme   │     │ harmonis_<outro> │
                   │  (hm_tenants)    │      │ (dados do tenant)│     │ (dados do tenant)│
                   └─────────────────┘      └──────────────────┘     └──────────────────┘
```

**Multi-tenancy (banco por tenant).** Um catálogo mestre (`harmonis_catalog`, tabela `hm_tenants`)
mapeia cada cliente ao seu banco (`harmonis_<slug>`). No login, o tenant é resolvido e um
`RoutingDataSource` (com pool de conexões por tenant) direciona todas as consultas ao banco certo.
**Nenhuma tabela de negócio tem `tenant_id`** — o isolamento é físico.

**Fluxo de uma requisição autenticada:**
1. O cliente envia `Authorization: Bearer <jwt>`.
2. Um filtro valida o token, extrai o tenant e as authorities, popula o `SecurityContext` e define o
   tenant corrente (contexto por thread, limpo ao fim da requisição).
3. O endpoint é autorizado por permissão (`@PreAuthorize("hasAuthority('...')")`).
4. O acesso a dados usa o datasource roteado para o banco do tenant.

**Decisões de projeto:**
- **Prefixo `hm_`** em todas as tabelas, para conviver com outros sistemas no mesmo banco.
- **Sem Flyway** — o schema é versionado em scripts SQL idempotentes em [`migrations/`](migrations/),
  aplicados manualmente; a aplicação roda com `spring.jpa.hibernate.ddl-auto=none`.
- **JSON em colunas** (`NVARCHAR(MAX)` com `CHECK(ISJSON(...) = 1)`) para o schema dos formulários,
  os fluxos e as respostas.
- **Arquivos em disco privado**, com nomes aleatórios e proteção contra *path traversal*; o conteúdo
  só é acessível por endpoint autenticado.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.5.6 (Web, Data JPA, Security, Validation), Maven |
| Banco | Microsoft SQL Server ≥ 2019 (driver mssql-jdbc, HikariCP) |
| Auth | JWT (jjwt) |
| Markdown | commonmark-java + jsoup (sanitização) |
| Frontend | Angular 20 (standalone components, signals, control flow), TypeScript 5.9, Angular CDK |

---

## Estrutura do repositório

```
.
├── backend/          API REST Spring Boot (Java 21, Maven)
│   ├── src/main/java/app/harmonis/
│   │   ├── auth/           login e emissão de JWT
│   │   ├── config/         datasources, propriedades
│   │   ├── documents/      ECM/GED
│   │   ├── forms/          formulários, schema, anexos
│   │   ├── identity/       usuários, papéis, permissões (ACL)
│   │   ├── notifications/  notificações in-app
│   │   ├── portal/         páginas Markdown
│   │   ├── security/       filtro JWT, configuração de segurança
│   │   ├── tenancy/        catálogo e roteamento por tenant
│   │   └── workflow/       motor de aprovação e processos
│   └── mvn.sh             wrapper do Maven (define JAVA_HOME p/ o JDK 21)
├── frontend/         SPA Angular (standalone + signals)
├── migrations/       Scripts SQL (SQL Server), aplicados manualmente
│   ├── 000_create_databases.sql        cria catálogo + tenant de demo (contexto: master)
│   ├── catalog/0001_create_tenants.sql hm_tenants + tenant "acme"
│   └── tenant/000X_*.sql               identidade, formulários, workflow, documentos, portal, anexos
├── tools/            Utilitários de desenvolvimento (ex.: MigrationRunner)
└── docs/             Especificação de domínio e handoff
```

---

## Começando

### Pré-requisitos

- **JDK 21** (LTS).
- **SQL Server ≥ 2019** acessível (por padrão em `localhost:1433`).
- **Node.js 20+** e npm (para o frontend).

### 1. Clonar

```bash
git clone <url-do-repositorio> harmonis
cd harmonis
```

### 2. Banco de dados e migrations

As credenciais são lidas de `backend/src/main/resources/application.yml` e podem ser sobrescritas por
variáveis de ambiente (ver [Configuração](#configuração)).

Não há Flyway: aplique os scripts na ordem com o runner incluído (`tools/MigrationRunner.java`, que
separa os *batches* por `GO`). Ajuste o caminho do driver e a senha:

```bash
JDK=java   # ou o caminho do seu JDK 21
# Ajuste a versão do driver conforme o que estiver no seu cache Maven (~/.m2):
JAR=$(find ~/.m2 -name 'mssql-jdbc-*.jre11.jar' ! -name '*sources*' | head -1)
OPTS=";encrypt=true;trustServerCertificate=true"

# 1) cria os bancos (contexto: master)
$JDK -cp "$JAR" tools/MigrationRunner.java \
  "jdbc:sqlserver://localhost:1433;databaseName=master$OPTS" sa '<senha>' \
  migrations/000_create_databases.sql

# 2) catálogo de tenants
$JDK -cp "$JAR" tools/MigrationRunner.java \
  "jdbc:sqlserver://localhost:1433;databaseName=harmonis_catalog$OPTS" sa '<senha>' \
  migrations/catalog/0001_create_tenants.sql

# 3) schema do tenant de demonstração (repita para cada script tenant/*.sql, em ordem)
for f in migrations/tenant/*.sql; do
  $JDK -cp "$JAR" tools/MigrationRunner.java \
    "jdbc:sqlserver://localhost:1433;databaseName=harmonis_acme$OPTS" sa '<senha>' "$f"
done
```

> O driver `mssql-jdbc` é baixado pelo Maven ao construir o backend; o `.jar` fica no cache local
> (`~/.m2/...`). Alternativamente, aplique os scripts com qualquer cliente SQL (sqlcmd, Azure Data Studio).

### 3. Backend

```bash
cd backend
./mvn.sh -DskipTests package
java -jar target/harmonis-backend.jar --harmonis.seed.tenant=acme
```

A flag `--harmonis.seed.tenant=acme` **semeia de forma idempotente** as permissões, os papéis de
sistema e o usuário dono do tenant de demonstração. A API sobe em `http://localhost:8080`.

**Login de demonstração:** `admin@acme.test` / `password`

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"tenant":"acme","email":"admin@acme.test","password":"password"}'
```

### 4. Frontend

```bash
cd frontend
npm install
npm start          # ng serve → http://localhost:4200
```

A base da API é definida em `frontend/src/app/core/api.ts` (`http://localhost:8080/api` por padrão).
Em produção, aponte-a para a URL pública da API (ou use um proxy reverso — ver abaixo).

---

## Configuração

Todas as propriedades têm padrões de desenvolvimento e podem ser sobrescritas por variáveis de
ambiente:

| Variável | Padrão | Descrição |
|---|---|---|
| `HARMONIS_DB_HOST` | `localhost` | Host do SQL Server |
| `HARMONIS_DB_PORT` | `1433` | Porta do SQL Server |
| `HARMONIS_DB_USER` | `sa` | Usuário do banco |
| `HARMONIS_DB_PASSWORD` | *(dev)* | Senha do banco |
| `HARMONIS_CATALOG_DB` | `harmonis_catalog` | Nome do banco de catálogo |
| `HARMONIS_JWT_SECRET` | *(dev)* | Segredo HMAC do JWT (**≥ 32 bytes; obrigatório em produção**) |
| `HARMONIS_JWT_TTL` | `480` | Validade do token, em minutos |
| `HARMONIS_CORS` | `http://localhost:4200,http://127.0.0.1:4200` | Origens permitidas (CSV) |
| `HARMONIS_DOCS_DIR` | `storage/documents` | Raiz dos arquivos (documentos e anexos) |

Limite de upload (multipart): **10 MB por arquivo / 15 MB por requisição** (em `application.yml`).

> **Segurança:** em produção defina `HARMONIS_JWT_SECRET` com um valor forte e exclusivo, use um
> usuário de banco com privilégios mínimos (em vez de `sa`) e sirva tudo sob HTTPS.

---

## Implantação em produção

A API e a SPA são artefatos independentes.

**1. Construir os artefatos**

```bash
# Backend → JAR executável
cd backend && ./mvn.sh -DskipTests package      # gera target/harmonis-backend.jar

# Frontend → estáticos
cd ../frontend && npm ci && npm run build        # gera dist/<app>/browser
```

**2. Executar a API** (com as variáveis de ambiente de produção)

```bash
HARMONIS_DB_HOST=... HARMONIS_DB_PASSWORD=... \
HARMONIS_JWT_SECRET=... HARMONIS_CORS=https://app.suaempresa.com \
java -jar harmonis-backend.jar
```

Rode como serviço gerenciado (`systemd`, container, etc.). Garanta que o diretório de
`HARMONIS_DOCS_DIR` seja **persistente** e esteja incluído na rotina de backup.

**3. Servir a SPA** com um servidor estático (Nginx, por exemplo) e encaminhar `/api` para a API,
evitando CORS e mantendo o mesmo domínio:

```nginx
server {
    listen 443 ssl;
    server_name app.suaempresa.com;

    root /var/www/harmonis;           # conteúdo de dist/<app>/browser
    location / { try_files $uri $uri/ /index.html; }   # rotas da SPA

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Ao servir atrás do mesmo domínio, aponte `API_BASE` (em `frontend/src/app/core/api.ts`) para `/api`
antes de construir o frontend.

**Checklist de produção**
- [ ] `HARMONIS_JWT_SECRET` forte e exclusivo
- [ ] Usuário de banco com privilégios mínimos (não `sa`)
- [ ] HTTPS ponta a ponta e `HARMONIS_CORS` restrito ao domínio da aplicação
- [ ] `HARMONIS_DOCS_DIR` persistente e com backup
- [ ] Backup por tenant (cada banco `harmonis_<slug>`)

---

## Provisionar um novo cliente (tenant)

1. Criar o banco do cliente (`harmonis_<slug>`) e registrar o tenant em `hm_tenants`
   (banco `harmonis_catalog`).
2. Aplicar todos os scripts `migrations/tenant/*.sql` nesse novo banco (na ordem).
3. Semear identidade e o usuário dono:
   ```bash
   java -jar harmonis-backend.jar --harmonis.seed.tenant=<slug>
   ```

---

## Referência da API

Base: `/api`. Salvo indicação, todas as rotas exigem JWT e a permissão listada.

<details>
<summary><strong>Autenticação &amp; sessão</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/health` | — | Saúde do serviço |
| POST | `/api/auth/login` | — | `{tenant,email,password}` → `{token,user}` |
| GET | `/api/me` | autenticado | Usuário autenticado (resolve o tenant pelo JWT) |

</details>

<details>
<summary><strong>Usuários &amp; papéis</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/users` | `users.view` | Lista usuários (`?q=` busca nome/e-mail) |
| POST | `/api/users` | `users.create` | Cria usuário (retorna senha inicial gerada) |
| PUT | `/api/users/{id}` | `users.update` | Atualiza nome/cargo/papéis/ativo |
| DELETE | `/api/users/{id}` | `users.delete` | Remove usuário (dono e o próprio usuário protegidos) |
| GET | `/api/roles` | `roles.view` | Lista papéis (sistema + personalizados) |
| GET | `/api/permissions` | `roles.view` | Catálogo de permissões agrupado |
| POST | `/api/roles` | `roles.create` | Cria papel personalizado |
| PUT | `/api/roles/{id}` | `roles.update` | Edita papel personalizado (sistema é imutável) |
| DELETE | `/api/roles/{id}` | `roles.delete` | Remove papel personalizado |

</details>

<details>
<summary><strong>Formulários, respostas &amp; anexos</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/forms/field-types` | `forms.view` | Catálogo de tipos de campo |
| GET | `/api/forms` | `forms.view` | Lista formulários (com contagens) |
| GET | `/api/forms/{id}` | `forms.view` | Detalhe (schema de campos) |
| POST | `/api/forms` | `forms.create` | Cria formulário |
| PUT | `/api/forms/{id}` | `forms.update` | Atualiza formulário/campos/status |
| DELETE | `/api/forms/{id}` | `forms.delete` | Remove formulário (e respostas) |
| POST | `/api/forms/{id}/submissions` | `forms.view` | Envia resposta (valida; 422 por campo) |
| GET | `/api/forms/{id}/submissions` | `forms.submissions` | Lista respostas |
| GET | `/api/forms/{id}/submissions.csv` | `forms.submissions` | Exporta CSV (`;` + BOM) |
| GET/PUT | `/api/forms/{id}/flow` | `forms.update` | Lê/salva o fluxo de aprovação (árvore) |
| POST | `/api/forms/{id}/attachments` | `forms.view` | Envia um anexo (multipart; `fieldKey`) |
| GET | `/api/forms/attachments/{id}/download` | `forms.view` | Baixa um anexo (autenticado) |

</details>

<details>
<summary><strong>Processos &amp; notificações</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/processes/inbox` | `processes.view` | Caixa de aprovações do usuário |
| GET | `/api/processes/mine` | `processes.view` | Minhas solicitações |
| GET | `/api/processes/{id}` | `processes.view` | Detalhe (dados + timeline) |
| POST | `/api/processes/tasks/{taskId}/decide` | `processes.view` | Aprova/reprova (`{approved,comment,fieldValues}`) |
| GET | `/api/notifications` | autenticado | Notificações recentes + não lidas |
| POST | `/api/notifications/{id}/read`, `/read-all` | autenticado | Marca como lida(s) |

</details>

<details>
<summary><strong>Documentos (ECM/GED)</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/documents?folder={id}` | `documents.view` | Navega (breadcrumb, pastas, docs) |
| GET | `/api/documents/search?q=` | `documents.view` | Busca (nome/descrição/metadados/arquivo) |
| GET | `/api/documents/{id}` | `documents.view` | Detalhe + versões |
| POST | `/api/documents` (multipart) | `documents.create` | Upload (cria doc v1) |
| POST | `/api/documents/{id}/versions` (multipart) | `documents.create` | Nova versão |
| PUT | `/api/documents/{id}` | `documents.update` | Nome/descrição/metadados |
| DELETE | `/api/documents/{id}` | `documents.delete` | Exclui (remove arquivos) |
| GET | `/api/documents/{id}/download` | `documents.view` | Baixa a versão atual (autenticado) |
| GET | `/api/documents/versions/{id}/download` | `documents.view` | Baixa uma versão |
| POST/PUT/DELETE | `/api/documents/folders[/{id}]` | `documents.*` | CRUD de pastas (exclusão recursiva) |

</details>

<details>
<summary><strong>Portal &amp; dashboard</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/portal` | `pages.view` | Lista páginas publicadas |
| GET | `/api/portal/{slug}` | `pages.view` | Página publicada (HTML sanitizado) |
| GET | `/api/pages` | `pages.update` | Lista todas (gestão; inclui rascunhos) |
| GET | `/api/pages/{id}` | `pages.update` | Página para edição (Markdown bruto) |
| POST | `/api/pages/preview` | `pages.update` | Pré-visualiza Markdown → HTML sanitizado |
| POST | `/api/pages` | `pages.create` | Cria página |
| PUT | `/api/pages/{id}` | `pages.update` | Atualiza/publica página |
| DELETE | `/api/pages/{id}` | `pages.delete` | Exclui página |
| GET | `/api/dashboard` | autenticado | Métricas + listas (condicionadas à permissão) |

</details>

---

## Modelo de permissões

As permissões são agrupadas por recurso e combinadas em papéis. Papéis de sistema não podem ser
editados; papéis personalizados são livres.

| Grupo | Permissões |
|---|---|
| Usuários | `users.view` · `users.create` · `users.update` · `users.delete` |
| Papéis | `roles.view` · `roles.create` · `roles.update` · `roles.delete` |
| Formulários | `forms.view` · `forms.create` · `forms.update` · `forms.delete` · `forms.submissions` |
| Processos | `processes.view` |
| Documentos | `documents.view` · `documents.create` · `documents.update` · `documents.delete` |
| Portal | `pages.view` · `pages.create` · `pages.update` · `pages.delete` |

| Papel de sistema | Acesso |
|---|---|
| **Administrador** (`admin`) | Todas as permissões |
| **Gestor** (`manager`) | Gestão de formulários, documentos, portal e processos; visão de usuários/papéis |
| **Colaborador** (`member`) | Preenche formulários, acompanha processos, visualiza o portal e envia documentos |

> O **dono do tenant** recebe todas as permissões, independentemente dos papéis.

---

## Testes

```bash
cd backend && ./mvn.sh test
```

Os testes cobrem o catálogo de ACL, o serviço de JWT, usuários e papéis, o schema e a validação de
formulários (incluindo anexos e segmentação por papel), o motor de workflow, o armazenamento de
documentos e a sanitização de Markdown.

---

## Roadmap

- [x] Fundação: catálogo + roteamento por tenant, autenticação JWT, identidade/RBAC, CORS
- [x] Usuários, papéis e permissões (`@PreAuthorize`)
- [x] Formulários (construtor, schema JSON, validações, respostas, CSV)
- [x] Workflow (motor de aprovação paralelo/condicional/gateway, caixa, timeline, notificações)
- [x] ECM/GED (pastas, versionamento, download seguro, metadados, busca)
- [x] Portal (páginas Markdown sanitizado)
- [x] Dashboard + modelador visual (arrastar-e-soltar, zoom, mini-mapa)
- [x] Construtor de formulários avançado (validações, seleção única/múltipla, layout responsivo)
- [x] Segmentação do formulário por papel (preenchimento durante o fluxo)
- [x] Campos de anexo (único/múltiplo) com limites de tipo e tamanho
- [ ] Provisionamento automático de tenant (criação do banco no cadastro de um cliente)
- [ ] Renovação/expiração de token no frontend
- [ ] Testes de integração ponta a ponta

---

## Contribuição

Contribuições são bem-vindas!

1. Faça um fork e crie um branch a partir de `main` (`git checkout -b feat/minha-mudanca`).
2. Siga os padrões do projeto:
   - Tabelas sempre com prefixo **`hm_`**; mudanças de schema como **novos scripts** em `migrations/`
     (nunca editar scripts já aplicados).
   - Acompanhe a mudança com **testes** e rode `./mvn.sh test` antes de abrir o PR.
   - Mensagens de commit curtas e descritivas (imperativo).
3. Abra um **Pull Request** descrevendo a motivação, as mudanças e como testar.

Para relatar bugs ou propor melhorias, abra uma **issue** com passos de reprodução, comportamento
esperado e o comportamento observado.

---

## Licença

Todos os direitos são reservados ao(s) autor(es).
