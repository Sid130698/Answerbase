# Workspace retrieval, generation and privacy settings

Workspace admins configure hybrid/vector/keyword retrieval, candidate count, answer context size, local reranking, passage size, embedding section context, prompt masking and a daily attempt budget under Access. Staff delegation uses the selected customer's admin permission. Defaults inherit server retrieval configuration until a workspace saves its own policy.

Generation model choices come from server configuration (`GENERATION_MODEL` plus optional comma-separated `GENERATION_MODELS`). Customers cannot provide provider URLs or keys. Operators must install/enable allowed models first. Embedding dimensions/model remain installation-level settings to avoid mixing incompatible vectors. Chunk size changes affect future text processing; section-context changes affect future indexing and manual index rebuilds. Existing immutable passages are not silently rewritten.

Prompt masking is on by default. It replaces email addresses, phone-like numbers and US SSN patterns with request-local opaque placeholders before generation. Quoted values are restored locally before exact-source citation validation, so original documents remain intact and authorized readers still see their sources. The placeholder map is never sent to the model. This is limited pattern matching, not comprehensive detection of names, addresses, international identifiers or obfuscated PII. Stored questions, source files and query logs are not anonymized. Local embedding/reranking services still process original content; hosted generation is unconfigured and has not been live-tested.

Daily attempt limits are enforced atomically alongside concurrent-query limits. They reset at midnight UTC, count generation attempts including retries, and exempt reuse of a completed idempotent request. A request budget is not a monetary spend guarantee: hosted token pricing remains unverified; local reports count API fees only.

The test suite checks admin/role boundaries, allowed-model validation, saved retrieval behavior, daily budget rejection, masking before the generation adapter and local restoration of exact quotes. `WorkspacePolicy` is shared by API and worker so settings do not depend on a particular frontend.

Local verification: 22 backend tests and frontend lint/build pass; browser checked saved-default controls and 390px layout with 45px selects/no page overflow. The real local-model smoke still returned the cited refund fact and abstained on the unsupported question (12.4s and 5.2s respectively; latency targets remain unmet).
