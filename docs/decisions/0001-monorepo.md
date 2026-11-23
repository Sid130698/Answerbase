# ADR 0001: Monorepo with separate runtime processes

Status: accepted for the initial scaffold.

Keep React, the Java API, the ingestion worker, migrations, evaluations, and deployment definitions in one repository.
Use Maven modules for the backend and a standalone npm project for the frontend.
API and worker are separate Spring Boot executables; shared core code must not depend on either application.

The API owns Flyway migration execution initially. The worker does not run migrations and starts after API readiness locally.
A single migration source is packaged as a dependency. Once rollout requirements grow, use a dedicated release migration step.

Cloudflare builds frontend/ independently. The API and worker can share one Oracle VM while remaining separate containers.
Supabase supplies persistent storage, authentication, and Postgres in the target deployment.
Availability of Oracle free capacity and public auth email delivery remain unresolved deployment prerequisites.
No cloud infrastructure is provisioned by this repository scaffold.

Repo layout does not force services to share a release or increase runtime cost. Rebuild/test affected components only;
changes to shared backend code or migrations must validate both backend applications.
