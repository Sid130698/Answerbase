# Render free deployment

Selected by the owner: Render (no payment method), Supabase Free, Groq Free, Brevo Free. Provisioning is in progress; this document is not evidence of a working deployment.

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
