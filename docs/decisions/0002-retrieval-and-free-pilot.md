# Retrieval and free-pilot tradeoffs

## PostgreSQL and exact vector scoring

PostgreSQL owns tenant membership, document-group rules, version state, full-text indexes and pgvector embeddings. Keeping the permission predicate and ranking inputs in one database makes the security boundary inspectable and transactionally consistent. A dedicated vector service would add replication, filter synchronization and another deployment dependency.

The current vector search is exact cosine scoring over authorized current versions, followed by reciprocal-rank fusion with PostgreSQL full-text candidates. It is not BM25 and does not use an approximate nearest-neighbor index. That trades throughput for predictable authorization/recall at this pilot's scale. An ANN migration must test filtered recall, particularly restrictive document groups, before replacing this path.

At 50,000 actual chunks, the original SQL materialized wide chunk rows and wrote temporary data before ranking. The release changes materialization to the small set of authorized version IDs and metadata, then joins chunks. Authorization still precedes candidate ranking. The representative old plan took 3.57s and wrote 6,241 temporary blocks; the revised plan took 2.78s without those temporary writes. These single plans are diagnostic observations, not a controlled latency distribution. The completed end-to-end scale report includes embedding and concurrency separately.

## Hybrid search, reranking and chunk context

Hybrid retrieval covers exact identifiers and semantic phrasing. The cross-encoder is a swappable local HTTP service; it costs CPU and latency while avoiding another hosted quota. The evaluation compares vector-only, hybrid, reranking, 600 versus 1,800-character chunks, and title/section context on/off using separate indexed workspaces. Default character sizes approximate the PRD token budget; no claim is made that one universal chunk size is optimal.

Small synthetic fixtures saturate hit@5 easily. Use their reciprocal rank and all-source coverage to expose ranking differences, then inspect the separate public PDF/handbook corpus. Human review is still needed to assess semantic claim support. Source-quote validation prevents nonexistent or altered citations; it cannot establish that a claim follows logically from its quote.

## Separate original storage and derived data

Originals are retained, never replaced by embeddings. Local development uses a private shared Docker volume; the hosted adapter uses a private Supabase bucket. The VM PostgreSQL database holds extracted text, versions, chunks and vectors. The managed free database is not used for application vectors: 50,000 768-dimensional float embeddings alone have a roughly 154 MB raw payload before row/index/text/TOAST overhead, leaving little room in a 500 MB managed database budget.

## Queue and failure boundaries

PostgreSQL leases provide durable ingestion, indexing, original-deletion and answer-retry state without adding a queue service. Workers are separate processes. If throughput later justifies an external broker, retain job identity, execution fencing and idempotent publication in PostgreSQL; publish job IDs through an outbox instead of treating broker delivery as exactly-once execution.

Provider retries use the saved question ID, backoff and current access checks. They do not silently send documents to a second provider. Hosted failover would require an approved destination and model-quality/cost comparison.

## Deployment

One free VM minimizes service count and supports independently built customer/staff applications, API and worker containers. It is a pilot topology, not high availability: VM reclamation/failure affects the whole app. Supabase provides identity/private originals and Groq handles generation; embeddings/reranking stay local. Terraform, production Compose, TLS, monitoring, checked-main deployment and verified backups make the setup reproducible. Actual account capacity, SMTP sender-domain control, DNS and off-host recovery still need the owner's accounts. No paid subscription is assumed.
