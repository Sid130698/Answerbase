# Workspace retrieval, generation and privacy settings

Workspace admins configure hybrid/vector/keyword retrieval, candidate count, answer context size, local reranking, passage size, embedding section context, prompt masking and a daily attempt budget under Settings. Staff delegation uses the selected customer's admin permission. Defaults inherit server retrieval configuration until a workspace saves its own policy.

Generation model choices come from server configuration (`GENERATION_MODEL` plus optional comma-separated `GENERATION_MODELS`). Customers cannot provide provider URLs or keys. Operators must install/enable allowed models first. Embedding dimensions/model remain installation-level settings to avoid mixing incompatible vectors. Chunk size changes affect future text processing; section-context changes affect future indexing and manual index rebuilds. Existing immutable passages are not silently rewritten.

Prompt masking is on by default. It replaces email addresses, phone-like numbers and US SSN patterns with request-local opaque placeholders before generation. Quoted values are restored locally before exact-source citation validation, so original documents remain intact and authorized readers still see their sources. The placeholder map is never sent to the model. This is limited pattern matching, not comprehensive detection of names, addresses, international identifiers or obfuscated PII. Stored questions, source files and query logs are not anonymized. Local embedding/reranking services still process original content; hosted generation is unconfigured and has not been live-tested.

Daily attempt limits are enforced atomically alongside concurrent-query limits. They reset at midnight UTC, count generation attempts including retries, and exempt reuse of a completed idempotent request. A request budget is not a monetary spend guarantee: hosted token pricing remains unverified; local reports count API fees only.

The test suite checks admin/role boundaries, allowed-model validation, saved retrieval behavior, daily budget rejection, masking before the generation adapter and local restoration of exact quotes. `WorkspacePolicy` is shared by API and worker so settings do not depend on a particular frontend.

Local verification: 22 backend tests and frontend lint/build pass; browser checked saved-default controls and 390px layout with 45px selects/no page overflow. The real local-model smoke still returned the cited refund fact and abstained on the unsupported question (12.4s and 5.2s respectively; latency targets remain unmet).

## Hosted cost estimates

Operators may set `GENERATION_PRICES_JSON` to exact returned `provider:model` identifiers and USD rates per million tokens, for example:

```text
GENERATION_PRICES_JSON={"groq:your-model":{"inputPerMillion":0.1,"outputPerMillion":0.2}}
```

Those numbers are illustrative, not current provider pricing. Verify your account's pricing before configuring them. Both rates may be explicitly zero for verified free access. Missing models remain unpriced, and malformed/negative rates prevent startup. The saved estimate uses actual reported input/output usage including the citation-verification retry; existing historical costs are not rewritten when rates change. Local Ollama API charges remain zero, excluding hardware/electricity. This is an estimate, not reconciliation against a provider invoice; requests interrupted before usage is returned can incur provider usage that the app cannot measure.
