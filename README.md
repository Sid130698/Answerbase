# Answerbase

Cited answers over company documents. This repository contains the initial monorepo scaffold; the RAG product is not implemented yet.

## Included

- Java 21 / Spring Boot API and an independently runnable worker shell.
- PostgreSQL + pgvector via Docker Compose; Flyway creates the vector extension and application schema.
- React + TypeScript frontend with a real backend-readiness check.
- Default-deny backend security: only health endpoints are publicly readable. JWT login and workspace authorization are next.
- Maven wrapper, npm lockfile, lint/format checks, integration smoke tests, and GitHub Actions CI.

## Start locally

Install Docker with Compose and start its daemon. No cloud credentials are required.

```sh
cp .env.example .env
docker compose up --build -d --wait
```

Open http://localhost:5173. API readiness: http://localhost:8080/actuator/health/readiness.
PostgreSQL is exposed locally on port 55432 to avoid colliding with a system installation.
The worker's management port is internal. All published development ports bind to localhost.

```sh
docker compose logs -f api worker
docker compose down
```

The named database volume persists across shutdowns. Credentials in `.env.example` are local-only defaults.

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
The worker is a readiness-enabled process shell: it does not ingest jobs yet.

Host-run Java commands read `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` from the process environment;
they do not automatically load the root `.env`. If changing the Compose database port or credentials, export the matching
Java variables. The frontend API base URL is configured with `VITE_API_BASE_URL` at build time; never put secrets in `VITE_*`.

## Verify

```sh
make test
```

This starts the local database, runs `./mvnw verify` from `backend/`, and runs frontend lint/typecheck/build.
Backend tests use real PostgreSQL: they check Flyway/pgvector bootstrap and that non-health routes are denied.
`make format` applies Java formatting. CI runs the same checks on Java 21 and Node 24, selecting frontend/backend jobs from changed paths.

## Repository layout

| Path | Responsibility |
| --- | --- |
| `frontend/` | React client, independent static build |
| `backend/api/` | HTTP API, security boundary, query orchestration |
| `backend/worker/` | Isolated background-processing application |
| `backend/core/` | Shared domain primitives; no API/worker dependencies |
| `backend/migrations/` | Single source of versioned database migrations |
| `evals/` | Evaluation plan; runner and gold dataset are not implemented |
| `infra/` | Container builds and local web proxy |
| `docs/` | Design index and architecture decisions |

## Target deployment

Frontend: Cloudflare Pages, root `frontend`, build `npm run build`, output `dist`.
API and worker: separate containers on one VM, subject to free-capacity availability.
Data/auth: Supabase. Public email signup requires an SMTP provider; provider selection is pending.

The local Compose stack is for development, not a hardened public deployment. No cloud accounts, paid resources,
deployment credentials, or remote Git repository are configured. CI has no deploy step and makes no LLM calls.

## Next implementation slice

1. Workspace/membership schema and Supabase JWT validation.
2. Protected workspace creation and access tests.
3. Document/version/job schema, private storage, and ingestion worker.
4. Permission-filtered retrieval, Groq generation, citations, and evaluation baseline.
5. Frontend signup, upload, chat, and source viewing.

See [the design](answerbase-design.html) and [architecture](answerbase-architecture.svg).
