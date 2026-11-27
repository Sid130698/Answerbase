# Historical local evaluation results — 2026-11-26

These measurements use a small, AI-authored candidate corpus, **not a human-reviewed gold benchmark**. It contains fictional Acorn policies, pricing and security documents, an archived contradiction, an injected instruction, and two attributed public science facts. The public-reference component is too small to represent a large messy document collection. This historical run predates the expanded public corpus and draft gold/difficulty annotations added in PR #16. Human gold approval and manual grading remain acceptance work; see the current PRD checklist.

Host: Apple M5, 10 logical CPUs, 16 GiB memory. Generation: local Ollama `llama3.1:8b` Q4_K_M. Embeddings: `nomic-embed-text:latest`, 768 dimensions. Reranker: local `cross-encoder/ms-marco-MiniLM-L6-v2`. API/worker/database run in Docker. These are developer-machine measurements, not a hosted capacity guarantee.

## Retrieval ablations

Each run includes 70 requests: 53 have an annotated gold source and 17 deliberately have no answer. Retrieval metrics exclude the 17 no-gold cases. Up to eight passages are returned, so the recorded reciprocal-rank metric is MRR@8.

| Variant | Hit@5 | MRR@8 | Request errors |
| --- | ---: | ---: | ---: |
| Vector only | 100% | 0.798 | 0 |
| Hybrid vector + PostgreSQL FTS / RRF | 100% | 0.820 | 0 |
| Hybrid + cross-encoder | 100% | 0.962 | 0 |
| Hybrid + cross-encoder, 600-character chunks | 100% | 0.962 | 0 |
| Hybrid + cross-encoder, no embedding context line | 100% | 0.962 | 0 |

Default chunk size is 1,800 characters. Most fixtures fit in one default chunk. The identical chunk/context results do not demonstrate that those settings are unimportant on real documents. FTS uses PostgreSQL `ts_rank_cd`, not BM25; the authorized vector query performs exact ranking over a materialized permission-filtered set, not HNSW approximate search.

## Fresh complete-answer run

After full-response deadline hardening, all 70 requests completed without request errors. The earlier run remains recorded separately with six failures (provider timeout, client timeout and subsequent capacity rejections); it is not a successful baseline. The fresh run was sequential without concurrent scale/ablation/model evaluation work.

| Measure | Result | Interpretation |
| --- | ---: | --- |
| Expected-unanswerable abstention | 17/17 (100%) | Candidate labels, including seven adversarial cases |
| False abstention | 2/53 (3.77%) | Two answerable candidates declined |
| Abstention precision | 17/19 (89.47%) | Of declined answers, fraction labeled unanswerable |
| Literal citation integrity | 100% | Quotes occur in supplied passages; not semantic support |
| Expected-term match proxy | 46/53 (86.79%) | Diagnostic only; not correctness |
| Complete-answer p50 | 5.264 s | Misses <4 s target |
| Complete-answer p95 | 13.107 s | Misses <8 s target |
| Input / output tokens | 76,648 / 6,083 | Scored questions only; follow-up parent setup excluded |
| Known API charges | $0 | Local inference; excludes hardware/electricity |

A separate stream request returned its first status event at 128 ms and first verified claim at 8.422 s, with final answer at 8.423 s. This misses the first-answer target of 1.5 s. Progress events are not answer tokens. The implementation validates the completed model response before emitting claims; it does not stream unverified raw model tokens.

Examples worth reviewing: the support-closing-time question received a fact about weekends instead of 17:00; a synthesis answer omitted invitation expiry; a follow-up about Team pricing abstained; an adversarial archived-policy question cited the historical 14-day rule. Conversely, the lexical proxy marked a correct-looking `10,000` answer wrong because its expected string was `10000`. These examples explain why neither quote matching nor term matching is an accuracy measure.

## Permission and scale probes

A live restricted-document probe first confirmed the admin could retrieve a newly indexed secret canary. A viewer in that same workspace could not retrieve it, received 404 for its original, and abstained on an adversarial request for its value. The temporary document and newly added fixture membership were removed afterward. Broader cross-organization/group boundaries are covered by integration tests.

A rollback-only test inserted 2,000 documents / 50,000 chunks with deterministic synthetic 768-dimensional vectors into the isolated test database. Single-query times: vector SQL 761.718 ms, keyword SQL 114.322 ms. A nonmember was denied. This does not measure real ingestion, embedding throughput, concurrent users, model generation, or end-to-end p95 at scale.

## Acceptance limits

Model rubric grades and a deterministic 20% human-review sample are generated separately. Same-model judge bias is possible. The first unstructured judge run produced 45 invalid/timeout grading results and is unusable; its partial means must not be reported as quality. The judge now requests a constrained schema and still validates every grade. Human agreement remains unmeasured until a person reviews the sample.

Raw reports are retained locally under `evals/reports/`; compact measured summaries are versioned in `docs/local-results.json`. The candidate baseline under `evals/baselines/` supports local machine-metric comparisons, not a claim of human-approved quality. See `evals/README.md` for exact commands and `prd-checklist.md` for outstanding release gates.

## Experimental rubric grades

The final counted-citation judge run produced 70 schema-valid grades with zero request/format failures: correctness 41/70 (58.57%), faithfulness 60/70 (85.71%), and supported citations 59/71 (83.10%). Citation accuracy is weighted over citations, not averaged over answer percentages. **These are uncalibrated Llama 3.1 judgments, not verified product accuracy.** Inspection found clearly questionable grading: the judge rejected an opening-time answer despite a 09:00–17:00 source range, and penalized an EU-storage answer for not discussing other regions that were not asked about. The model is therefore not a reliable acceptance oracle yet. Neither passing a JSON schema nor running all cases establishes judge reliability.

A deterministic 14-case independent review packet and blank CSV have been generated locally. Machine grades are withheld from the packet. No human review or agreement score has been invented. Use `evals/human-review.mjs` only after a person completes that CSV; calibrate or replace the judge before using its scores to approve a release.
