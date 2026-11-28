# Render free deployment

Selected by the owner: Render (no payment method), Supabase Free, Groq Free, Brevo Free. Live pilot: https://answerbase-sid.onrender.com (verified 2026-11-28). No payment method or paid service was added. Render serves application commit `37daf13`; the separately deployed Edge Function matches this repository. Auto-deploy is off; releases are manual.

The Render Docker service packages the customer frontend behind Nginx and one Java API process. `EMBEDDED_WORKER=true` enables the existing durable ingestion, indexing and lifecycle processors inside that process. The separate worker remains the default for local/VM deployments. Never run a second embedded worker just to bypass platform limits or keep the service awake.

Supabase holds the application PostgreSQL schema, Auth users and a private document bucket. The `embeddings` Edge Function uses hosted gte-small. Its normalized 384-dimensional vectors are padded with zeros to fit the existing 768-dimensional column, preserving cosine distance. A distinct model identity prevents mixing these with local nomic embeddings. Old documents require explicit reindexing when changing provider; local evaluation scores do not certify the new model.

Set `EMBEDDING_SECRET` independently in the Edge Function and Render server environment. Disable the Edge gateway JWT check only for this function; it explicitly rejects requests without the matching server secret. Never expose this secret, Groq key, database password or storage service key in Vite variables.

Required Render environment:

- `DATABASE_URL`: JDBC URL for the Supabase session pooler, with TLS enabled.
- `DATABASE_USERNAME`, `DATABASE_PASSWORD`: database connection credentials.
- `SUPABASE_AUTH_ISSUER`: project URL plus `/auth/v1`.
- `DOCUMENT_STORAGE_PROVIDER=supabase`, `DOCUMENT_STORAGE_URL`: project URL.
- `DOCUMENT_STORAGE_BUCKET=answerbase-documents`, `DOCUMENT_STORAGE_SERVICE_KEY`.
- `GENERATION_PROVIDER=groq`, `GROQ_API_KEY`, `GENERATION_MODEL`: verified available Groq model.
- `EMBEDDING_ENDPOINT`: project URL plus `/functions/v1/embeddings`; `EMBEDDING_SECRET`.
- Public build variables `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`, `VITE_SUPPORT_ORIGIN`.

Use `infra/render/Dockerfile`, Free instance, port 10000, and readiness path `/actuator/health/readiness`. Choose a region near the database (the created Supabase project is Tokyo). No Render Postgres is used: its free database expires after 30 days. No local persistent files are relied on.

The free web service sleeps after inactivity. Queue processing pauses while asleep and resumes on wake; upload polling supplies normal user traffic while processing. It is a limited pilot, not always-on production. Keep reranking disabled unless a separately configured supported provider is available. Brevo phone verification and sender verification are prerequisites for testing real signup/recovery emails.

Before claiming completion: test the 512 MB container, apply migrations, configure private storage and allowed auth redirects, configure Brevo SMTP in Supabase, test a real confirmed signup/sign-in, upload and index a document, ask a cited question, retrieve its original, and confirm another workspace cannot access it. Verify all four accounts remain on free plans without payment methods.


## Hosted verification — 2026-11-28

- Readiness returned HTTP 200 and all 15 Flyway migrations applied.
- A real signup confirmation arrived in Gmail through Brevo and its confirmation link succeeded. Password login and authenticated `/api/v1/me` returned 200. Browser login opened the correct organization/workspace.
- Created an organization and workspace; uploaded a 216-byte Markdown policy. Supabase private storage retained the original, and the embedded worker extracted and indexed both sections using hosted gte-small.
- Groq `openai/gpt-oss-20b` returned the expected 30-day refund answer with an exact source citation. The browser rendered the answer, citation highlight, source passages and indexed status. An unsupported question abstained.
- Original-file API returned 200 with the expected contents. Anonymous original access returned 401; a different workspace path returned 404. Unauthenticated embedding requests returned 401.
- These are pilot smoke checks, not a complete load test or corpus quality evaluation. UI download automation timed out; original retrieval was independently verified through the public API.

The runtime initially did not receive the Edge Function secret despite its dashboard row. Saving the same value again resolved it; verify a real authorized embedding call after provisioning. Missing runtime configuration now returns 503 rather than misreporting invalid credentials.

Brevo rewrites the verified Gmail sender to its authenticated `brevosend.com` domain. A custom sender domain is not configured. The private document adapter currently uses the legacy service-role key; migrate it to the created modern secret key before Supabase retires legacy keys. No secrets belong in this document or Git. Support frontend is not deployed with the customer site.
