# PRD coverage and acceptance

Source: `PRD Answerbase, Cited Q&A over Messy Company Docs.pdf`, Draft v1. This is a coverage record, not a claim that the PRD is complete. Owner decisions supersede the draft: one organization per customer account, uniform admin/editor/viewer workspace roles, independently deployed Answerbase staff UI, and local setup before hosted accounts. SSO/domain enrollment and OCR are future work.

## Product requirements

| PRD requirement | Implemented behavior | Remaining work or acceptance limitation |
| --- | --- | --- |
| 7.1 Upload and extraction | PDF, DOCX, Markdown, HTML, TXT; batch/drop upload; headings, tables, pages and offsets; versions, hashes, duplicate signals | Scanned PDFs require OCR, explicitly a stretch goal. Public PDF and handbook acceptance runs are recorded separately below. |
| 7.1 Connector and queue | Incremental Notion-export folder connector; isolated parsing worker; persistent jobs, retry/backoff and failed records | Connector deliberately retains remotely missing files; it does not mirror deletions. |
| 7.2 Chunking and indexing | Structure-first chunks, overlap, source offsets, embedding identity, optional title/section context; vector and text indexes | Character limits approximate token targets. Chunk-size experiments use a tiny corpus and do not establish a preferred production setting. |
| 7.3 Retrieval | Authorized rows selected before ranking; vector + PostgreSQL FTS, RRF, optional cross-encoder; configurable candidates/top-k; short follow-up context | PostgreSQL text ranking is not BM25. The full pipeline scale harness uses API uploads and actual embeddings, not injected SQL rows; execution evidence is recorded below. |
| 7.4 Generation and verification | Ollama and Groq-compatible clients; cited JSON claims; exact passage/quote validation; one verification retry then abstention; persisted history | Literal quote checks do not establish semantic entailment. Hosted provider unverified. Citation-checked complete claims now stream incrementally; reset events clear provisional output on verification retry. Latency targets remain measured separately. |
| 7.4 Source freshness | Source title and last-updated date alongside completed answers; date also in evidence viewer | Dates describe stored source versions, not independently verified policy effective dates. |
| 7.5 Source viewer | Citation links open highlighted extracted text; permission-checked original download | PDF evidence uses extracted text with page references rather than a PDF overlay viewer. |
| 7.6 Access and tenancy | One org/user, explicit workspace membership, document groups, pre-ranking permission predicate, separate staff identities and audited delegation | Automated API boundaries and live restricted-document canary pass; production SSO/domain enrollment deferred by owner. |
| 7.7 Administration | Source status/version/failure details, query log, weekly usage and known costs, unanswered/negative-feedback CSV, invitations, members/groups, model/retrieval/privacy/retention settings | Unknown provider prices are explicitly unpriced. No hosted billing validation. |
| 7.8 Feedback | Helpful/not-helpful reasons and notes; corrected answers queued as evaluation candidates | Candidates require review before becoming trusted gold. |
| 8 Reliability | Ingestion retries; query idempotency, admission limits, execution leases, provider deadlines and recovery; document deletion outbox | Provider failures queue durable bounded retries under the original question ID, with current access checks, leases, expiry and backoff. No silent cross-provider fallback. |
| 8 Performance | Local answer measurements and a real 2,000-document/50,000-chunk ingestion/concurrent-search harness | Full-run evidence is recorded below. Local default per-workspace quotas are 200 versions / 100 MiB; production configuration allows 3,000 versions and enforces an additional global 800 MiB originals ceiling. |
| 8 Security/privacy | JWT validation, tenant authorization, isolated parsing, private originals, prompt masking, retention/deletion, server-owned model credentials | Production HTTPS/Caddy and data-boundary documentation supplied. Database/original backup restoration verified locally. Hosted encryption, account retention controls, Auth recovery and off-host copies require actual provider setup. Masking is deliberately limited, not comprehensive PII protection. |
| 8 Observability | Query traces plus a continuous monitor for service health, queue failures, stuck work and p95 latency | Monitor runs every minute, emits aggregate JSON and supports an HTTPS alert webhook. Local health and latency-alert execution verified; external delivery requires an operator endpoint. |
| 8 Maintainability | API/worker/core modules, Flyway migrations, typed provider contracts, lint/build, core coverage gate | Latest full local core coverage: 74.92% (672/897 lines); required gate is 70%. Backend and both frontend CI jobs pass at aaf7a95. |

## Evaluation and release requirements

| PRD requirement | Evidence | Acceptance state |
| --- | --- | --- |
| 10.1 Licensed messy public corpus + synthetic company | Thirteen synthetic Markdown fixtures; pinned 311-file GitLab support handbook plus license; three NIST PDFs including tables; scanned-only PDF rejection test | Harnesses use actual uploads, extraction, embeddings and queries. Full public-corpus execution is in progress; semantic review remains human work. |
| 10.2 60 hand-written gold questions + 10 adversarial | 60 AI-authored candidates + 10 adversarial, draft reference answers, difficulty labels, category and exact anchor checks; all comparisons have two source documents | **Human review pending:** difficulty and reference drafts are supplied, but AI drafts cannot satisfy the human-written/reviewed gold gate. |
| 10.3 Metrics, ablations and human calibration | 70-case answer runner, five retrieval variants, schema-validated judge, blind 20% review packet and agreement calculator | Fresh runs use the revised corpus; older numbers are historical. Semantic correctness/citation accuracy remain uncalibrated until genuine reviewer grades are supplied. |
| 10.4 Regression gate in CI | Live GitHub baseline/candidate jobs run a 14-case subset spanning every category and local judges (all 70 on manual runs) with identical harness/model digests; comparison rejects incompatible runs and >3-point drops | Workflow execution is in progress. Machine comparison is against the PR base, not a human-approved gold baseline; human calibration remains pending. |
| 9 / 11 Public deployment and CI/CD | Oracle Terraform/cloud-init, production Compose/Caddy, preflight/deploy command, checked-main host updater, local bootstrap and PR checks | Terraform validate and Compose validation pass. Real provisioning, DNS/TLS, hosted authentication/email/storage/generation and public URL require accounts; they are not claimed as executed. |
| 11 / 16 Release proof | Runtime diagram, design notes, evaluation/cost reports, operations guide, 90-second captured-screen demo and longer walkthrough | Demo is an edited sequence of real running-product captures, not continuous recording. Updated corpus measurements/write-up are in progress; public URL requires hosting. |
| 5 / 16 External validation | None claimed | A local tester worksheet/export is supplied. Five genuine external testers, three consented quotes and a pilot follow-up require real participants; no opinions are fabricated. |

## Measured targets

The completed local candidate run had 70/70 successful requests, hit@5 100%, abstention 17/17 and false abstention 2/53. Complete-answer latency was p50 5.264s / p95 13.107s, missing the 4s / 8s targets. A separate stream probe produced its first verified claim after 8.422s, missing 1.5s. These are prior measured results, not a fresh benchmark for every code change. Literal citation integrity is not the PRD's semantic citation-accuracy metric. Local model API charges were zero; hardware and operating costs were excluded.

Details: [local evaluation](local-evaluation-results.md), [machine-readable results](local-results.json), [operations](local-operations.md), [evaluation workflow](../evals/README.md).

All feature work goes through `feature/` branches and PR review/checks. Passing CI does not imply PRD acceptance or authorize merging unreviewed branches.

## Release artifacts

[Deployment instructions](free-deployment.md), [walkthrough/video](walkthrough.html), [tester worksheet](tester-session.html), [data boundaries](data-boundaries.md), [resolved dependency inventory](dependency-inventory.json). Review PR #16 and its checks before merging; this record does not grant merge approval.
