# Document ingestion

Original files are retained. Embeddings cannot reconstruct a PDF and are not a replacement for the original.

## Storage

The default `DOCUMENT_STORAGE_PROVIDER=local` stores originals in a persistent Docker volume named `answerbase_document-data`. API writes to `/app/documents`; worker mounts it read-only. This is private filesystem storage, not a public static directory. Host-run API and worker must set `DOCUMENT_STORAGE_ROOT` to the same absolute directory (their working directories can differ).

For separate-server deployment, select `DOCUMENT_STORAGE_PROVIDER=supabase` in both processes. Create a **private** `answerbase-documents` bucket in Supabase Storage with no client read/write policies. Configure `DOCUMENT_STORAGE_URL=https://PROJECT.supabase.co`, `DOCUMENT_STORAGE_BUCKET`, and `DOCUMENT_STORAGE_SERVICE_KEY` with a server-only service_role key. Never use VITE variables for this key. API and worker use the same bucket. This adapter is included; a hosted bucket has not been provisioned or tested with this repository's credentials. Supabase's free file-storage quota is 1 GB as checked November 26, 2026: https://supabase.com/docs/guides/storage/pricing. Database and bandwidth quotas are separate.

Do not change providers on an existing installation without copying originals under their existing keys and verifying checksums. Back up both the database and original-file storage; one cannot reconstruct the other.

Keys have the shape `organizations/{org}/workspaces/{workspace}/documents/{document}/versions/{version}/original`. They organize bytes, not permissions. Only the API can issue original downloads after checking current membership. Downloads are attachments with no-store and nosniff headers.

## Model and authorization

Each document belongs to exactly one workspace. The API resolves the organization from the authenticated user. All versions and extracted chunks carry organization/workspace scope, protected by composite foreign keys. Editor/admin can upload and retry; viewer can list, read extracted passages and download originals. Organization ownership does not bypass workspace membership. The support customer frame uses the same service permissions, with an audited scoped grant; staff bearer tokens are never sent into the frame.

The new routes are under `/api/v1/workspaces/{workspace}/documents`. Swagger describes list/upload, version history/upload, original download, paginated chunks and retry. Each upload requires a UUID Idempotency-Key. An identical retry returns the existing version; changing bytes/name/target with the same key is rejected.

## Processing

1. Validate membership, filename/extension, size, PDF signature and quota. Lock the workspace to serialize quotas and version numbers.
2. Save original bytes privately and atomically insert document/version/job records. Queue jobs become visible only after commit. On database rollback the API attempts to remove the object.
3. Worker claims one pending job using PostgreSQL SKIP LOCKED, with a five-minute lease and attempt token. It reads the stored original and verifies SHA-256.
4. Apache PDFBox extracts page text. UTF-8 TXT/Markdown preserves line ranges. Text is split into bounded passages; PDF passages overlap slightly.
5. Publish chunks and `processed` status in one transaction only if the worker still owns the lease. The highest successfully processed version remains available even if a newer version fails.
6. Retry failed processing automatically up to three attempts, with 30 seconds between attempts. Dead-worker leases are reclaimed; an editor can explicitly retry a final failure.

Statuses: queued, processing, processed, failed. **Processed means extracted text, not ready for semantic Q&A.** Embeddings, retrieval, Groq calls and answer generation are not implemented in this slice. Originals can be downloaded even if extraction fails. The Sources UI polls active jobs every three seconds and offers version history and source passage previews.

## Bounds and limitations

- PDF, TXT, MD only; 5 MB per original, 200 pages per PDF, one million extracted characters.
- 100 MB and 200 versions per workspace in this preview, including older/failed versions. These per-workspace limits are not a hosted account-wide quota guarantee.
- OCR, connectors, bulk uploads, document deletion/retention UI and cross-workspace sharing are future work.
- PDFs are parsed in the separate worker container with a 512 MB memory ceiling. No external links are fetched and no scripts are executed. Full malware scanning is not included.
- Database and object storage cannot share a transaction. An abrupt API crash after writing an object can leave an unreachable orphan. Rollback cleanup handles ordinary failures; a periodic orphan reconciler remains future work. Never make the bucket public to expose these objects.
- Local volume works for API/worker on one Docker host. Multiple hosts require shared object storage.
- A running request may finish while membership is revoked; subsequent requests re-check access. No long-lived download links are issued.

## Validation

Integration tests use real PostgreSQL and real JWT validation. They cover viewer upload denial, cross-organization and same-organization cross-workspace reads, original-byte round trip, idempotency/conflicting retries, composite foreign keys, extraction, retries, stale-worker leases, failed replacement retention and revoked membership. Core tests cover PDF page references, text line references, encoding rejection and local storage path traversal.

Local browser validation: uploaded Markdown through Sources and observed the independent worker publish passages; checked desktop and 390px mobile layout, keyboard navigation and a missing-document error. Original download byte integrity is covered by the API test; the browser download-event observer timed out, so browser file-save completion is not claimed. Both frontend lint/builds and all 14 backend/core tests pass locally. Hosted Supabase Storage remains unprovisioned and unverified end to end.

## PRD ingestion extension

DOCX and HTML are now accepted alongside PDF/TXT/Markdown. HTML is parsed with jsoup without fetching resources; scripts and active elements are removed. DOCX uses bounded ZIP expansion and a namespace-aware XML parser with DTDs/external entities disabled. Headings, lists and table cells become readable source text. Passage records include section paths and exact UTF-16 offsets into stored extracted text; offsets do not describe byte positions inside the original file. Long paragraphs overlap; paragraphs/headings/page boundaries otherwise guide chunking. Configurable character limits approximate the PRD token target and need evaluation with the chosen tokenizer.

Up to 20 files can be selected/dropped in one UI batch; requests are individually idempotent and failed files can be retried without resending successful files. Unchanged replacement content returns the latest version without reprocessing. Editors can review exact hash matches and heuristic near-duplicate candidates (word-set Jaccard >=0.85); this heuristic is a review aid, not an automatic deletion decision. V7 queues existing processed files once to populate the new coordinates.
