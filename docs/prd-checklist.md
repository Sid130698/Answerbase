# PRD coverage and acceptance

Source: `PRD Answerbase, Cited Q&A over Messy Company Docs.pdf`, Draft v1. This is a coverage record, not a claim that the PRD is complete. Owner decisions supersede the draft: one organization per customer account, uniform admin/editor/viewer workspace roles, independently deployed Answerbase staff UI, and local setup before hosted accounts. SSO/domain enrollment and OCR are future work.

## Product requirements

| PRD requirement | Implemented behavior | Remaining work or acceptance limitation |
| --- | --- | --- |
| 7.1 Upload and extraction | PDF, DOCX, Markdown, HTML, TXT; batch/drop upload; headings, tables, pages and offsets; versions, hashes, duplicate signals | Scanned PDFs require OCR, explicitly a stretch goal. Large messy public corpus is not yet validated. |
| 7.1 Connector and queue | Incremental Notion-export folder connector; isolated parsing worker; persistent jobs, retry/backoff and failed records | Connector deliberately retains remotely missing files; it does not mirror deletions. |
| 7.2 Chunking and indexing | Structure-first chunks, overlap, source offsets, embedding identity, optional title/section context; vector and text indexes | Character limits approximate token targets. Chunk-size experiments use a tiny corpus and do not establish a preferred production setting. |
| 7.3 Retrieval | Authorized rows selected before ranking; vector + PostgreSQL FTS, RRF, optional cross-encoder; configurable candidates/top-k; short follow-up context | PostgreSQL text ranking is not BM25. Exact vector scoring has only a synthetic SQL scale measurement. |
| 7.4 Generation and verification | Ollama and Groq-compatible clients; cited JSON claims; exact passage/quote validation; one verification retry then abstention; persisted history | Literal quote checks do not establish semantic entailment. Hosted provider unverified. SSE sends status immediately but verified claims only after full generation; first-answer target missed. |
| 7.4 Source freshness | Source title and last-updated date alongside completed answers; date also in evidence viewer | Dates describe stored source versions, not independently verified policy effective dates. |
| 7.5 Source viewer | Citation links open highlighted extracted text; permission-checked original download | PDF evidence uses extracted text with page references rather than a PDF overlay viewer. |
| 7.6 Access and tenancy | One org/user, explicit workspace membership, document groups, pre-ranking permission predicate, separate staff identities and audited delegation | Automated API boundaries and live restricted-document canary pass; production SSO/domain enrollment deferred by owner. |
| 7.7 Administration | Source status/version/failure details, query log, weekly usage and known costs, unanswered/negative-feedback CSV, invitations, members/groups, model/retrieval/privacy/retention settings | Unknown provider prices are explicitly unpriced. No hosted billing validation. |
| 7.8 Feedback | Helpful/not-helpful reasons and notes; corrected answers queued as evaluation candidates | Candidates require review before becoming trusted gold. |
| 8 Reliability | Ingestion retries; query idempotency, admission limits, execution leases, provider deadlines and recovery; document deletion outbox | Generation failure saves the question and supports manual resubmission. Automatic queued generation retry/fallback is not implemented. |
| 8 Performance | Local answer measurements and 2,000-document/50,000-chunk synthetic SQL probe | Full ingestion, concurrent load, and target latency remain unverified or missed. Upload quotas are deployment configuration; local defaults remain 200 retained versions / 100 MiB. |
| 8 Security/privacy | JWT validation, tenant authorization, isolated parsing, private originals, prompt masking, retention/deletion, server-owned model credentials | Local HTTP is not production TLS. Hosted encryption/backups, comprehensive PII review and subprocessor verification remain deployment work. |
| 8 Observability | Query timing records/logs and local diagnostic command for latency, ingestion failures and stuck work | Diagnostic script is not a continuous monitoring/alerting service. Production error tracking and alert delivery remain pending. |
| 8 Maintainability | API/worker/core modules, Flyway migrations, typed provider contracts, lint/build, core coverage gate | Latest measured core coverage: 77.96%; exact rerun evidence belongs in test reports. |

## Evaluation and release requirements

| PRD requirement | Evidence | Acceptance state |
| --- | --- | --- |
| 10.1 Licensed messy public corpus + synthetic company | Six small Markdown fixtures, including a small attributed public component and contradictory policy examples | **Incomplete:** not a large public corpus; missing representative PDF/table benchmark coverage. |
| 10.2 60 hand-written gold questions + 10 adversarial | 60 AI-authored candidates + 10 adversarial; category and exact anchor checks | **Incomplete:** human-written/reviewed gold answers and difficulty labels are required. Candidate fixtures cannot satisfy this gate. |
| 10.3 Metrics, ablations and human calibration | 70-case local run, five retrieval variants; 14-case blind human-review packet; schema-validated local judge | **Partial:** judge produced spurious grades; semantic correctness/citation accuracy remain uncalibrated. No human grades supplied. |
| 10.4 Regression gate in CI | Regression comparison command and contract tests reject incompatible runs and >3-point drops | **Incomplete:** CI tests the gate's logic; it does not run live quality evaluation against a reviewed baseline. |
| 9 / 11 Public deployment and CI/CD | Container images, Compose, local bootstrap and PR checks | **Local only:** no hosted accounts supplied; no public demo, hosted TLS or verified deploy pipeline. |
| 11 / 16 Release proof | Runtime diagram, design notes, measured evaluation/cost reports and operations guide | Demo recording, walkthrough, large-corpus write-up and hosted URL remain outstanding. |
| 5 / 16 External validation | None claimed | Five genuine external testers, three usable quotes and a pilot follow-up require real participants. |

## Measured targets

The completed local candidate run had 70/70 successful requests, hit@5 100%, abstention 17/17 and false abstention 2/53. Complete-answer latency was p50 5.264s / p95 13.107s, missing the 4s / 8s targets. A separate stream probe produced its first verified claim after 8.422s, missing 1.5s. These are prior measured results, not a fresh benchmark for every code change. Literal citation integrity is not the PRD's semantic citation-accuracy metric. Local model API charges were zero; hardware and operating costs were excluded.

Details: [local evaluation](local-evaluation-results.md), [machine-readable results](local-results.json), [operations](local-operations.md), [evaluation workflow](../evals/README.md).

All feature work goes through `feature/` branches and PR review/checks. Passing CI does not imply PRD acceptance or authorize merging unreviewed branches.
