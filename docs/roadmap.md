# Product roadmap

A feature count depends on granularity. After document ingestion, five major milestones remain for an initial usable hosted Q&A product; these are not five small tasks.

1. **Team onboarding and access UI:** approved organization invitations/joining and workspace member management. Backend workspace membership exists; onboarding currently needs a trusted local script.
2. **Embeddings and authorized retrieval:** select/configure an embedding model, embed chunks, pgvector indexing, workspace-filtered search, document-version selection and reindexing.
3. **Cited Q&A:** Groq/provider adapter, grounded prompts, answers with page/line citations, original-source navigation, no-answer behavior and conversation history.
4. **Quality and document lifecycle:** gold-set evaluations, cross-workspace leakage tests, answer feedback, deletion/retention including originals and embeddings, and orphan cleanup.
5. **Hosted operation:** provision free-tier services within actual quotas, secrets/backups, monitoring, account-wide usage limits, staff SSO/MFA and production access review. Free availability and capacity are not guaranteed.

Completed foundation: monorepo/CI, Supabase authentication, organizations and workspace roles, separate staff support console and audited delegation. Document ingestion adds originals, versions, worker processing, retries, source passage previews and downloads.

Optional later scope: OCR, external connectors, bulk upload, custom company domains/customer SSO, cross-workspace document sharing and richer support diagnostics.
