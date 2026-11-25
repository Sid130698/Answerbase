# PRD delivery checklist

Source: `PRD Answerbase, Cited Q&A over Messy Company Docs.pdf` (Draft v1). Later owner decisions take precedence: one organization per customer account; uniform admin/editor/viewer workspace roles; separate Answerbase staff identities and frontend; free-tier-first operation. OCR remains a stretch goal. No claim of completion without the listed evidence.

Work proceeds on feature branches with tests after each slice, then PR review and CI before merge. Dependent branches may stack on earlier unmerged feature branches.

| Slice | Requirements | Status / evidence |
| --- | --- | --- |
| Foundation | Auth, org isolation, workspace membership, separate staff support | Implemented, PR #1 merged |
| Original ingestion | Private originals, PDF/TXT/MD, versions, queue, retries, source preview | PR #2 open; 14 tests locally; desktop/mobile upload verified |
| Complete ingestion | DOCX/HTML, structure/offsets, batch/drop uploads, unchanged-content dedupe, duplicate signals | Implemented on feature/prd-ingestion; 17 tests pass, frontend lint/build pass; desktop/390px batch upload and processing verified |
| Document authorization | Named groups, document restrictions, pre-ranking SQL filtering, staff parity | Implemented: shared database access predicate, groups/admin UI; 17 tests pass. Retrieval integration follows in next slice. |
| Retrieval | Embedding interface, pgvector + FTS, RRF, reranker interface, configurable variants | Implemented; 17 tests pass; live local embedding + cross-encoder smoke passed; scale/ablation benchmarks pending |
| Answers | Hosted provider, streaming, grounded claims, citation/quote verification + retry/fallback, history | Pending |
| Source viewer | Citation links, highlighted passages, page/section and updated timestamps | Pending |
| Customer administration | Invitations/approval, workspace members/groups, settings, query log, weekly costs | Pending |
| Feedback | Votes/reasons, unanswered CSV, corrected-answer evaluation queue | Pending |
| Lifecycle/connector | Retention, full deletion, orphan reconciliation, Notion-export folder sync | Pending |
| Security/operations | Rate limits, PII redaction, traces, alerts, provider failures, budgets | Pending |
| Evaluation | 60 gold questions + 10 adversarial cases, real metric reports, ablations, CI regression gate | Pending; human-written/reviewed gold and 20% grading spot-check require owner participation |
| Release | Core coverage >=70%, load test 2,000 docs/50,000 chunks, reproducible deployment, license record, README/design notes | Pending |
| External proof | Public URL with two workspaces, demo video, write-up, five external testers/three quotes, pilot follow-up | External evidence required; do not fabricate testers, quotes, live results or deployment |

Quality targets: hit@5 >=90%; correctness >=85%; citation accuracy >=95%; unanswerable abstention >=90%; false abstention <10%; p50 <4s / p95 <8s complete answer, first stream <1.5s; cost <$0.03/answered query. Report actual misses openly. Hosting and live provider verification depend on supplied accounts; no paid services without explicit budget.

Owner update: no hosted accounts yet. Prepare and verify local setup first; hosted deployment/live Groq results remain pending.
