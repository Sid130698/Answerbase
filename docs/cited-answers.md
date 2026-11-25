# Cited answers and source verification

Ask uses the signed-in user's current workspace permissions. Retrieval filters documents in SQL before ranking. The generation adapter supports local Ollama and a server-side Groq-compatible endpoint; no hosted account is configured or required for local use.

The model receives authorized passages as untrusted evidence. Each claim must cite a supplied passage ID and an exact quotation (whitespace normalized). Unknown IDs, missing citations and invented quotations fail validation. One repair attempt is allowed, followed by an abstention. This validates quote provenance, **not semantic entailment or answer correctness**; those require evaluation and human review.

Conversation context includes the previous two questions only for explicit follow-up wording. Unrelated questions must be answered independently. Access is checked again before publication and whenever a saved answer is opened. Questions and feedback are private to their author; an administrator query-log surface is a separate feature.

The UI streams status and verified claims using SSE. The model response is buffered until validation; this is not raw-token streaming and does not yet meet the first-answer latency target. Citations open a supporting passage, then the version-specific source with a highlighted passage, full extracted text, and original download. Text is rendered inertly, never as document-supplied HTML.

## Local verification

Run the retrieval smoke first to upload/index the synthetic refund policy, then `node scripts/smoke-answer.mjs`. This signs into local Supabase with the gitignored local test account; no credentials are printed. It asserts a cited 30-day refund answer and abstention for an unsupported home-address question. These two examples are smoke checks, not the PRD quality benchmark.

Use `GENERATION_PROVIDER=ollama`, `GENERATION_MODEL=llama3.1:8b`, and an accessible `OLLAMA_URL`. Hosted use requires a server-only `GROQ_API_KEY` and a compatible model name. Hosted requests and pricing have not been verified. Local cost recorded as zero means API charges only; compute and electricity are excluded.

## Known remaining work

Query leases/admission limits and administrative logs are implemented in subsequent slices (see `query-reliability.md` and `query-administration.md`). Retention/deletion, PII controls and evaluation reports remain tracked in `prd-checklist.md`. Do not treat this as production readiness.

Verified locally on 2026-11-26: 18 backend tests pass; frontend lint/build pass. Live llama3.1:8b answered the refund case in 11.8 s and declined the unsupported question in 6.5 s after a conversation-context correction. The initial test exposed an irrelevant carry-over answer; that regression remains in the smoke script. These timings miss the PRD latency targets. Desktop browser citation/highlight/feedback checks pass; the mobile override did not apply, so mobile validation is pending.
