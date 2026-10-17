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

- [Manual do usuário](#manual-do-usuário)
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
- [Internacionalização (i18n)](#internacionalização-i18n)
- [Sessão e segurança dos tokens](#sessão-e-segurança-dos-tokens)
- [Integrações (webhooks)](#integrações-webhooks)
- [Referência da API](#referência-da-api)
- [Modelo de permissões](#modelo-de-permissões)
- [Testes](#testes)
- [Roadmap](#roadmap)
- [Contribuição](#contribuição)
- [Licença](#licença)

---

## Manual do usuário

Há um **manual completo e ilustrado** (HTML navegável, com busca, tema escuro e capturas de tela numeradas)
em [`docs/manual/`](docs/manual/index.html). Abra `docs/manual/index.html` no navegador — não exige servidor nem build.
Ele descreve cada tela, cada campo e cada fluxo: usuários e papéis, construtor de formulários, fluxo de aprovação,
processos, documentos, portal, integrações (webhooks) e logs, além de glossário, FAQ e resumo de implantação.

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
- Autenticação por **JWT de curta duração** (15 min; carrega tenant, papéis e permissões) + **refresh token rotativo**, ambos em **cookies HttpOnly** (nada em `localStorage`), com proteção **CSRF** (double-submit). Veja *Sessão e segurança dos tokens*.
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
- **Internacionalização**: interface em **português (Brasil), inglês e espanhol**, com idioma preferido por usuário (*Meu perfil*) e formulários vinculados a um ou mais idiomas (só quem tem o idioma os vê para preencher).
- Exportação das respostas em **CSV** e **ficha em PDF** de cada registro (rótulo/valor na grade do layout do formulário, com histórico de aprovação).

### Workflow / BPM
- Motor de aprovação que opera sobre uma árvore de etapas, com suporte a:
  - **Aprovadores por papel ou por usuário**; etapas com **múltiplos aprovadores** (modo *todos* ou *qualquer um*).
  - **Gateways condicionais** (ramos "se verdadeiro / se falso") avaliados a partir dos dados da resposta.
  - **Etapas condicionais** (executadas somente quando uma condição é satisfeita).
- **Modelador visual** com arrastar-e-soltar, zoom e mini-mapa.
- **Caixa de aprovações**, acompanhamento das próprias solicitações e **timeline** por processo.
- **Notificações** in-app (sino) para solicitações e conclusões.

### Internacionalização (i18n)

| Item | Como funciona |
|---|---|
| **Idiomas** | `pt-BR` (padrão), `en` e `es`. Coluna `hm_users.language` (migration `0009`); o administrador define no cadastro e o próprio usuário em **Meu perfil** (`PUT /api/me/language`) |
| **Textos da interface** | Chave = texto-fonte em português; dicionários `frontend/src/app/core/i18n/{en,es}.ts`. Sem tradução, cai no português. Em templates use `{{ 'Texto' | t }}` (com parâmetros: `t:{n: x}`); em TypeScript, `tr('Texto', {n: x})` |
| **Troca de idioma** | Grava a preferência (`localStorage: harmonis.lang`) e recarrega a página, o que também aplica o *locale* de datas |
| **Formulários por idioma** | `hm_forms.languages` (CSV, ao menos um). A lista, o `GET /api/forms/{id}` e o envio só aceitam o idioma do usuário — exceto quem tem `forms.create`/`forms.update`, que vê todos (404 para os demais) |
| **PDF** | A ficha em PDF sai no idioma do usuário que a baixa (rótulos, situação, formato de data) |
| **Mensagens da API** | O backend responde em português; o interceptor do front traduz as mensagens conhecidas (`tr()`), e as demais permanecem em português |
| **Não traduzido** | Conteúdo escrito por pessoas (nomes de formulários/campos/etapas, portal) e mensagens técnicas com trechos dinâmicos |

Para adicionar um idioma: incluí-lo em `Languages.SUPPORTED` (backend), em `LANGS`/`normalize` e criar o dicionário no front.

## Sessão e segurança dos tokens

| Item | Como funciona |
|---|---|
| **Access token** | JWT HS256 de **15 min** (`iss`, `aud`, `jti`, `sub`, tenant, papéis, permissões; emissor/audiência validados). Cookie `hm_at`: **HttpOnly**, `Path=/api`, `SameSite=Strict`, `Secure` em produção |
| **Refresh token** | Valor **opaco** de 256 bits (`<tenant>.<segredo>`); no banco fica só o **hash SHA-256** (`hm_refresh_token`). Cookie `hm_rt`: **HttpOnly**, `Path=/api/auth` (só vai aos endpoints de sessão) |
| **Rotação** | Cada `/refresh` invalida o token usado e entrega outro, na mesma *família* (sessão). Validade deslizante de 7 dias, limitada a 30 dias absolutos |
| **Detecção de reuso** | Reapresentar um token já usado (fora da tolerância de 10 s, que cobre abas simultâneas) **revoga a família inteira** — quem roubou e quem perdeu a sessão são desconectados |
| **Permissões atualizadas** | A cada renovação o usuário é relido do banco: papéis/permissões novos passam a valer em até 15 min; usuário desativado perde a sessão |
| **XSS** | Nenhum token fica acessível ao JavaScript nem em `localStorage`/`sessionStorage` |
| **CSRF** | *Double-submit*: mutações exigem `X-XSRF-TOKEN` = cookie `XSRF-TOKEN`; mais `SameSite=Strict`. Login e refresh são isentos (sem sessão a proteger / só operam com o cookie de refresh) |
| **Frontend** | Em `401` o interceptor faz **uma** renovação compartilhada e repete as requisições; se falhar, volta ao login. Ao recarregar a página a sessão é restaurada por `/api/me` |
| **Logout** | Revoga a família no servidor e apaga os cookies |

> **Desenvolvimento:** acesse o front por `http://localhost:4200` (e não `127.0.0.1`) enquanto a API estiver em `localhost:8080`:
> hosts diferentes são *sites* diferentes e os cookies `SameSite=Strict` não acompanham as requisições.

> **Implantação:** com `HARMONIS_COOKIE_SECURE=true` (HTTPS) e, de preferência, front e API sob o **mesmo site**
> (mesmo domínio ou subdomínios + `HARMONIS_COOKIE_DOMAIN`), já que cookies `SameSite` não acompanham requisições
> entre sites diferentes. Os tokens vencidos são limpos a cada login.

## Integrações (webhooks)
- **Cadastro reutilizável** de webhooks de saída, com ciclo *rascunho → publicada*: só integrações
  publicadas podem ser usadas nos fluxos.
- **Vínculo por formulário e situação**: cada formulário dispara integrações diferentes **ao enviar**,
  **ao aprovar**, **ao reprovar** e **ao concluir** o fluxo (a mesma integração pode ser reaproveitada em vários formulários).
- **Autenticação** do destino: usuário e senha (Basic), API Key (cabeçalho ou query), Bearer estático,
  **Bearer com pré-autenticação** (login com usuário e senha, token em cache e renovado) e
  **cabeçalhos customizados**; opcionalmente **assinatura HMAC-SHA256** do corpo.
- **Entrega assíncrona** após o commit, com **retentativas** em falhas transitórias.
- **Logs de integração** por tentativa (conteúdo enviado, resposta, HTTP, duração), com filtros,
  **reenvio manual** e botão **Testar**.
- Áreas de cadastro e de logs com **permissões próprias**.

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
1. O navegador envia o cookie HttpOnly `hm_at` (access JWT) junto com a requisição.
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
│   │   ├── integrations/   webhooks de saída: cadastro, entrega, logs
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
│   └── tenant/000X_*.sql               identidade, formulários, workflow, documentos, portal, anexos, integrações
├── tools/            Utilitários de desenvolvimento (ex.: MigrationRunner)
└── docs/manual/      Manual do usuário (HTML navegável e ilustrado)
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
curl -s -c cookies.txt -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"tenant":"acme","email":"admin@acme.test","password":"password"}'
curl -s -b cookies.txt localhost:8080/api/me     # a sessão vai nos cookies (hm_at / hm_rt)
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
| `HARMONIS_JWT_TTL` | `15` | Validade do **access token**, em minutos |
| `HARMONIS_REFRESH_TTL_DAYS` | `7` | Validade (deslizante) do refresh token, em dias |
| `HARMONIS_REFRESH_ABSOLUTE_DAYS` | `30` | Duração máxima de uma sessão, mesmo com renovações |
| `HARMONIS_REFRESH_GRACE` | `10` | Tolerância (s) para refresh concorrente (abas) antes de tratar como reuso |
| `HARMONIS_COOKIE_SECURE` | `false` | `true` em produção (HTTPS): cookies só trafegam por conexão segura |
| `HARMONIS_COOKIE_SAMESITE` | `Strict` | `Strict`, `Lax` ou `None` (`None` exige `secure=true`) |
| `HARMONIS_COOKIE_DOMAIN` | *(vazio)* | Domínio dos cookies, se front e API estão em subdomínios (ex.: `.empresa.com`) |
| `HARMONIS_CORS` | `http://localhost:4200,http://127.0.0.1:4200` | Origens permitidas (CSV) |
| `HARMONIS_DOCS_DIR` | `storage/documents` | Raiz dos arquivos (documentos e anexos) |
| `HARMONIS_SECRET_KEY` | *(dev)* | Chave de cifragem (AES-256-GCM) dos segredos das integrações (**obrigatória em produção; não a perca**) |
| `HARMONIS_WEBHOOK_ALLOW_PRIVATE` | `false` | `true` permite webhooks para redes privadas/loopback (ver [Integrações](#integrações-webhooks)) |
| `HARMONIS_WEBHOOK_POOL` | `4` | Threads de entrega de webhooks |
| `HARMONIS_WEBHOOK_RETRY_DELAYS` | `5,30,120,300,600` | Espera (s) antes de cada retentativa |

Limite de upload (multipart): **10 MB por arquivo / 15 MB por requisição** (em `application.yml`).

> **Segurança:** em produção defina `HARMONIS_JWT_SECRET` e `HARMONIS_SECRET_KEY` com valores fortes
> e exclusivos, use um usuário de banco com privilégios mínimos (em vez de `sa`) e sirva tudo sob HTTPS.
> Trocar `HARMONIS_SECRET_KEY` torna **ilegíveis** os segredos de integrações já gravados (será preciso
> informá-los novamente).

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

## Integrações (webhooks)

Permite enviar os dados de um formulário a sistemas externos (ERP, CRM, automações) quando algo
acontece no processo de aprovação.

```
Cadastrar integração ─▶ Publicar ─▶ Vincular no fluxo do formulário ─▶ Evento ocorre ─▶ Entrega + log
 (URL, autenticação)    (rascunho→    (ao enviar · ao aprovar ·         (commit no       (assíncrona,
                         publicada)    ao reprovar)                      banco)           com retentativas)
```

1. **Integrações** (menu): cadastre o destino (URL, método, autenticação, cabeçalhos, tempo limite e
   retentativas). Use **Testar** para enviar um evento de exemplo — funciona em rascunho — e **Publicar**.
2. **Editor de fluxo** do formulário (`/forms/{id}/flow`): em *Integrações (webhooks)*, escolha quais
   integrações publicadas disparam **ao enviar**, **ao aprovar**, **ao reprovar** e **ao concluir** (fim do ciclo, aprovado ou reprovado — o resultado vem em `process.status`). Uma integração pode
   atender vários formulários e eventos; cada situação pode usar integrações diferentes.
3. **Logs de integração** (menu): acompanhe cada tentativa, abra o conteúdo enviado e a resposta, filtre
   por status/evento/integração/período e **reenvie** manualmente.

### Autenticação suportada

| Tipo | O que o Harmonis envia |
|---|---|
| Sem autenticação | Apenas os cabeçalhos padrão e os customizados |
| Usuário e senha (Basic) | `Authorization: Basic base64(usuário:senha)` |
| API Key | Nome/valor da chave em um **cabeçalho** ou **parâmetro de query** |
| Bearer token | `Authorization: Bearer <token>` (prefixo configurável) |
| Bearer com pré-autenticação | Antes de enviar, faz login na URL informada (usuário e senha, corpo JSON ou *urlencoded*, campos extras como `grant_type`), extrai o token da resposta (caminho configurável, ex.: `data.token`), guarda em cache até expirar (`expires_in` ou validade padrão) e o envia como Bearer. Se o destino responder **401**, renova o token e refaz o envio uma vez |
| Cabeçalhos customizados | Quaisquer cabeçalhos (inclusive `Authorization` próprio); valores podem ser marcados como **secretos** |

Em todos os tipos é possível definir um **segredo de assinatura**: cada envio leva
`X-Harmonis-Signature: sha256=<HMAC-SHA256 do corpo>` para o destino validar a origem.

### O que é enviado

`POST`/`PUT`/`PATCH` com `Content-Type: application/json` e os cabeçalhos
`X-Harmonis-Event` (`submitted` · `approved` · `rejected` · `completed` · `test`),
`X-Harmonis-Delivery` (identificador da entrega — o mesmo nas retentativas, útil para idempotência)
e, se configurado, `X-Harmonis-Signature`. Exemplo de corpo:

```json
{
  "event": "approved",
  "occurredAt": "2026-10-16T00:20:40Z",
  "tenant": "acme",
  "form": { "id": 11, "name": "Solicitação de compra", "slug": "solicitacao-de-compra" },
  "submission": {
    "id": 21,
    "submittedAt": "2026-10-16T00:20:37Z",
    "submittedBy": { "id": 1, "name": "Administrador", "email": "admin@acme.test" },
    "data": { "campo_a1b2c3": "Compra de notebook" },
    "answers": [
      { "key": "campo_a1b2c3", "label": "Assunto", "type": "text", "value": "Compra de notebook" }
    ]
  },
  "process": {
    "id": 7,
    "status": "approved",
    "steps": [
      { "step": "Gestor", "stepIndex": 0, "approver": "papel: manager", "status": "approved",
        "decidedBy": { "id": 1, "name": "Administrador", "email": "admin@acme.test" },
        "comment": "ok", "decidedAt": "2026-10-16T00:20:40Z" }
    ]
  }
}
```

Anexos aparecem como referências (`id`, `name`, `size`, `mime`); os arquivos **não** são enviados.

Validação da assinatura no destino (exemplo em Python):

```python
import hmac, hashlib
esperado = "sha256=" + hmac.new(SEGREDO.encode(), corpo_bruto, hashlib.sha256).hexdigest()
valido = hmac.compare_digest(esperado, request.headers["X-Harmonis-Signature"])
```

### Entrega, retentativas e logs

- Os eventos são tratados **depois do commit** da transação de origem (um *rollback* nunca dispara
  webhook) e em **threads próprias**: a aprovação não espera nem falha por causa do destino.
- **Retentativas** (até `retries` da integração, 0–5; espera conforme `HARMONIS_WEBHOOK_RETRY_DELAYS`)
  apenas em falhas transitórias: erro de rede, tempo esgotado, HTTP 408, 429 e 5xx. Respostas 4xx,
  redirecionamentos e erros de configuração **não** são repetidos.
- Cada tentativa gera um registro (`success` · `error` · `skipped`) com URL (segredos mascarados),
  conteúdo enviado, resposta (truncada), HTTP e duração. Entregas a integrações **despublicadas**
  ou excluídas são registradas como `skipped`. Testes e reenvios manuais aparecem marcados como *manual*.
- A integração só pode ser **excluída** quando não estiver vinculada a nenhum formulário; os logs
  permanecem após a exclusão.

### Segurança

- **Segredos cifrados em repouso** (AES-256-GCM, chave `HARMONIS_SECRET_KEY`) e **nunca devolvidos
  pela API**: a tela mostra apenas "definido"; deixar o campo em branco ao editar mantém o valor.
- **Proteção contra SSRF**: apenas `http`/`https`, sem credenciais embutidas na URL, redirecionamentos
  **não são seguidos** e o endereço de destino é validado após a resolução de DNS. Faixas *link-local*
  (`169.254.0.0/16`, incluindo o endpoint de metadados de nuvem) são **sempre bloqueadas**; loopback e
  redes privadas (10/8, 172.16/12, 192.168/16, 100.64/10, `fc00::/7`) são bloqueadas por padrão —
  defina `HARMONIS_WEBHOOK_ALLOW_PRIVATE=true` para integrar com sistemas da rede interna.
- Cabeçalhos customizados são validados (nomes padrão, sem quebra de linha) e os reservados
  (`Host`, `Content-Length`, `Content-Type`, `X-Harmonis-*`…) são recusados.
- Cadastro e logs têm permissões distintas (ver [Modelo de permissões](#modelo-de-permissões));
  quem edita formulários enxerga apenas **nome** das integrações publicadas, para vinculá-las.

### Limitações conhecidas

- A fila de entrega é **em memória**: se a aplicação reiniciar com retentativas ainda pendentes, elas
  são perdidas (o log mostra a última tentativa e o **reenvio manual** está disponível).
- Os logs não são expurgados automaticamente; planeje uma rotina de retenção para `hm_integration_log`.
- A ordem entre eventos de um mesmo processo não é garantida entre integrações diferentes (use o
  `occurredAt` do corpo).

---

## Referência da API

Base: `/api`. Salvo indicação, todas as rotas exigem sessão (cookie `hm_at`) e a permissão listada; requisições que alteram dados (POST/PUT/PATCH/DELETE) enviam também o header `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`.

<details>
<summary><strong>Autenticação &amp; sessão</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/health` | — | Saúde do serviço |
| POST | `/api/auth/login` | — | `{tenant,email,password}` → `{user}`; define os cookies `hm_at` (access), `hm_rt` (refresh) e `XSRF-TOKEN` |
| PUT | `/api/me/language` | autenticado | `{language}` (`pt-BR`\|`en`\|`es`) → usuário; define o idioma preferido |
| POST | `/api/auth/refresh` | cookie `hm_rt` | Rotaciona o refresh token e emite novo access token → `{user}`; 401 se inválido/expirado/reusado |
| POST | `/api/auth/logout` | cookie + CSRF | Revoga a sessão (refresh token) e limpa os cookies |
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
| GET | `/api/forms/{id}/submissions/{sid}/pdf` | `forms.submissions` | Ficha do registro em PDF |
| GET | `/api/processes/{id}/pdf` | `processes.view` | Ficha em PDF do registro do processo |
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
<summary><strong>Integrações &amp; logs</strong></summary>

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/integrations` | `integrations.view` | Lista integrações (com uso e último envio) |
| GET | `/api/integrations/options` | `integrations.view` **ou** `forms.update` | Integrações **publicadas** (id/nome), para vincular no fluxo |
| GET | `/api/integrations/{id}` | `integrations.view` | Detalhe (segredos nunca retornam; só indicadores "definido") |
| GET | `/api/integrations/{id}/usage` | `integrations.view` | Formulários/eventos que usam a integração |
| POST | `/api/integrations` | `integrations.create` | Cria (sempre como rascunho) |
| PUT | `/api/integrations/{id}` | `integrations.update` | Atualiza (segredo vazio = mantém o atual) |
| DELETE | `/api/integrations/{id}` | `integrations.delete` | Exclui (409 se vinculada a formulários) |
| POST | `/api/integrations/{id}/publish` · `/unpublish` | `integrations.publish` | Publica / volta a rascunho |
| POST | `/api/integrations/{id}/test` | `integrations.update` | Envia evento de exemplo e devolve o resultado |
| GET | `/api/integration-logs` | `integrations.logs` | Lista paginada (`status`, `event`, `integrationId`, `formId`, `from`, `to`, `q`, `page`, `size`) |
| GET | `/api/integration-logs/stats` | `integrations.logs` | Sucessos/erros/ignorados nas últimas 24 h |
| GET | `/api/integration-logs/{id}` | `integrations.logs` | Detalhe com conteúdo enviado e resposta |
| POST | `/api/integration-logs/{id}/resend` | `integrations.logs` **e** `integrations.update` | Reenvia o mesmo conteúdo (entrega manual) |
| PUT | `/api/forms/{id}/flow` | `forms.update` | Além de `nodes`, aceita `hooks`: `{submitted:[ids], approved:[ids], rejected:[ids], completed:[ids]}` (omitido = não altera) |

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
| Integrações | `integrations.view` · `integrations.create` · `integrations.update` · `integrations.delete` · `integrations.publish` · `integrations.logs` |

| Papel de sistema | Acesso |
|---|---|
| **Administrador** (`admin`) | Todas as permissões |
| **Gestor** (`manager`) | Gestão de formulários, documentos, portal e processos; visão de usuários/papéis |
| **Colaborador** (`member`) | Preenche formulários, acompanha processos, visualiza o portal e envia documentos |

> As permissões de **Integrações** são concedidas apenas ao Administrador por padrão (envolvem
> credenciais de sistemas externos); atribua-as a papéis personalizados conforme necessário — por
> exemplo, `integrations.logs` isolado para um perfil de auditoria.

> O **dono do tenant** recebe todas as permissões, independentemente dos papéis.

---

## Testes

```bash
cd backend && ./mvn.sh test
```

Os testes cobrem o catálogo de ACL, o serviço de JWT, usuários e papéis, o schema e a validação de
formulários (incluindo anexos e segmentação por papel), o motor de workflow, o armazenamento de
documentos, a sanitização de Markdown e as **integrações** (cifragem de segredos, proteção SSRF, cada tipo de
autenticação contra um servidor HTTP local, retentativas e validações do cadastro).

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
- [x] Integrações (webhooks) por evento do fluxo, com autenticação, retentativas e logs
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
