# Answerbase

Cited answers over company documents. Local authentication, organization/workspace access, document ingestion, hybrid retrieval, verified cited answers, feedback and query administration are implemented. See [the PRD checklist](docs/prd-checklist.md) for verified scope and remaining work.

## Included

- Java 21 / Spring Boot API and an independently runnable ingestion worker.
- PostgreSQL + pgvector via Docker Compose; Flyway creates the vector extension and application schema.
- React + TypeScript product preview with Ask, Sources, and an evidence panel. Sources supports uploads, processing status, version history, original downloads and extracted passages. Ask supports local-model answers, abstention, conversation history and version-specific citations.
- Default-deny backend security: health and enabled API documentation endpoints are publicly readable. Supabase JWT authentication protects application APIs; organization isolation and explicit workspace memberships enforce access.
- Self-service organization creation, one organization per account, workspace creation/switching, and workspace membership APIs. See [organization permissions](docs/organizations.md).
- Maven wrapper, npm lockfile, lint/format checks, integration smoke tests, and GitHub Actions CI.

- Independent Answerbase staff console in `support-frontend/`, with dedicated staff login, scoped customer delegation and audited workspace access repairs. See [support setup](docs/support.md).

## Start locally

Install Node 24, Docker with Compose, and Ollama; start Docker. No cloud credentials are required. The local bootstrap uses existing model downloads, or downloads nomic-embed-text and llama3.1:8b (about 5 GB in total) if absent.

```sh
make local
```

Open http://localhost:5173. API readiness: http://localhost:8080/actuator/health/readiness.
PostgreSQL is exposed locally on port 55432 to avoid colliding with a system installation.
Swagger UI: http://localhost:5173/swagger-ui/index.html (or API port 8080). OpenAPI JSON: http://localhost:5173/v3/api-docs.
The worker's management port is internal. All published development ports bind to localhost.

```sh
docker compose logs -f api worker
docker compose down
```

The named database and document volumes persist across shutdowns. Credentials in `.env.example` are local-only defaults. `make local` initializes local signing keys, starts Supabase if needed, copies only public browser configuration, selects local Ollama inference, and starts the application containers. It preserves existing data and does not provision staff. Confirm signup emails in local Mailpit at http://localhost:55324. Optional reranker setup is in [retrieval](docs/retrieval.md).

## Develop without container rebuilds

Use Java 21+, Node 24 LTS, and Docker for Postgres. CI and runtime containers use Java 21.

```sh
docker compose up -d --wait db
cd backend
./mvnw -DskipTests package
java -jar api/target/answerbase-api-0.1.0-SNAPSHOT.jar
```

In another terminal, `cd frontend && npm ci && npm run dev`.
Vite proxies `/actuator` and `/api` to localhost:8080. The worker can run separately using
`java -jar backend/worker/target/answerbase-worker-0.1.0-SNAPSHOT.jar` after the API has applied migrations.
The worker polls the PostgreSQL ingestion queue. See [document ingestion](docs/document-ingestion.md) for shared private storage setup and limits.

Host-run Java commands read `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` from the process environment;
they do not automatically load the root `.env`. If changing the Compose database port or credentials, export the matching
Java variables. The frontend API base URL is configured with `VITE_API_BASE_URL` at build time; never put secrets in `VITE_*`.

## Verify

```sh
make test
```

This creates the separate `answerbase_test` database if absent, runs `./mvnw verify`, enforces shared-core line coverage >=70%, and checks both frontends, the connector, and evaluation contracts. Python 3 is required for the coverage gate. Tests do not reset the application database. For direct Maven test runs, first run `make test-db`; CI provisions its own test database. Override `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` and `TEST_DATABASE_PASSWORD` only with a dedicated test database.
Backend tests use real PostgreSQL: they check Flyway/pgvector bootstrap, JWT signature and claim validation, and default-deny route authorization.
`make format` applies Java formatting. CI runs the same checks on Java 21 and Node 24, selecting frontend/backend jobs from changed paths.

## Repository layout

| Path | Responsibility |
| --- | --- |
| `frontend/` | React client, independent static build |
| `backend/api/` | HTTP API, security boundary, query orchestration |
| `backend/worker/` | Isolated background-processing application |
| `backend/core/` | Shared domain primitives; no API/worker dependencies |
| `backend/migrations/` | Single source of versioned database migrations |
| `evals/` | Local candidate corpus, evaluation runner, metric/regression contracts |
| `infra/` | Container builds and local web proxy |
| `docs/` | Design index and architecture decisions |

[Organization invitations and member administration](docs/invitations.md) use approved, email-bound enrollment with no hosted SMTP dependency.

Document deletion, opt-in retention and local orphan reconciliation are described in [lifecycle](docs/document-lifecycle.md).

## Folder imports

An incremental [Notion-export folder connector](docs/folder-sync.md) uploads supported files and creates versions for changed content. It uses normal workspace permissions and a private local sync manifest.

## Target deployment

Frontend: Cloudflare Pages, root `frontend`, build `npm run build`, output `dist`.
API and worker: separate containers on one VM, subject to free-capacity availability.
Data/auth: Supabase. Public email signup requires an SMTP provider; provider selection is pending.

The local Compose stack is for development, not a hardened public deployment. No cloud accounts, paid resources,
or deployment credentials are configured. The GitHub repository and review PRs are configured. CI has no deploy step and makes no LLM calls.

## Remaining delivery work

Shared-core coverage is measured at 77.72% (558/718 lines). Evaluation and scale verification remain in progress; see [the local evaluation instructions](evals/README.md). Workspace model/retrieval settings, limited prompt masking and daily request budgets are documented in [workspace settings](docs/workspace-settings.md). Public deployment and external user evidence require accounts and participation. Follow [the acceptance checklist](docs/prd-checklist.md); do not treat this preview as a completed production release.

See [the design](answerbase-design.html) and [architecture](answerbase-architecture.svg).

## API documentation

Springdoc generates OpenAPI from implemented routes. Swagger is served at `/swagger-ui/index.html`,
and JSON at `/v3/api-docs`. Health, authenticated identity, organization creation, workspace creation/read, and workspace membership operations are documented.
Both the local Nginx frontend and Vite dev server proxy these paths to the API. Swagger executes against the same origin.

Set `API_DOCS_ENABLED=false` to disable the spec/UI and their public security allowlist.
Documentation does not grant access to protected API routes. Tests check this boundary and the disabled configuration.
When adding controllers, annotate request/response behavior and verify the generated contract. No hand-maintained endpoint catalog is needed.

## UI design system

[DESIGN.md](DESIGN.md) defines typography, colors, spacing, motion, and critique expectations derived from the requested design references.
[frontend/AGENTS.md](frontend/AGENTS.md) carries those rules into future UI tasks. Fonts are self-hosted; there are no runtime font-service calls.
Customer navigation contains Ask and Sources. Swagger remains separate developer tooling.

## Authentication

Email/password registration, email confirmation, login, recovery, and sign-out use Supabase Auth. The API verifies access tokens at `GET /api/v1/me`. See [local and hosted auth setup](docs/authentication.md). Organization enrollment and workspace membership are implemented; document ingestion is implemented. See [organization setup](docs/organizations.md).
