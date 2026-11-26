# Delivery status and next gates

The local product path is implemented: authentication, organization/workspace enrollment, members/groups, five-format ingestion, private originals and versions, folder sync, authorized hybrid retrieval, reranking, cited answers/history, source inspection, feedback/corrections, query administration, settings, retention/deletion, and a separate staff console with audited delegation.

Release checks include an isolated test database, core coverage gate, local candidate evaluation, retrieval ablations, a synthetic 50,000-chunk SQL probe and operational diagnostics. See `prd-checklist.md` for exact evidence and `local-results.json` for recorded measurements. Open feature PRs require review before merge.

Remaining acceptance gates are substantial:

1. Human-written/reviewed gold answers, passage anchors and difficulty labels; expand the very small public-reference corpus into a representative licensed document collection. Candidate fixture results are not a production quality claim.
2. Rubric grading and a 20% human spot-check with agreement measurement. Investigate wrong answers and false abstentions; local latency currently misses the targets.
3. A trusted model-equipped CI runner with an approved baseline. Ordinary PR CI checks evaluation contracts, not live model quality.
4. Full ingestion and concurrent-query testing at the target scale. The SQL probe uses synthetic vectors and one query at a time.
5. Hosted accounts, public TLS/storage/secrets/backup verification, monitoring destinations and a complete dependency-license review. No hosted or paid resources are provisioned.
6. Public demo URL, video/write-up, five external testers/three genuine quotes and pilot follow-up. These require real participation and cannot be fabricated.

OCR remains stretch scope. Company SSO/domain enrollment and tenant hostnames remain future adapters, per owner direction. Free hosted capacity is not assumed; current verified operation is local.
