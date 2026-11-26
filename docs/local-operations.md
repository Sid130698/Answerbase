# Local release checks and operations

No hosted accounts are needed for this workflow. `make local` starts Supabase, application containers and host Ollama; see README for prerequisites. The cross-encoder is optional and has a separate setup in `retrieval.md`. Use separate customer and staff accounts.

## Health and failures

Run `node scripts/diagnose-local.mjs`. It checks API readiness, Auth, Ollama, optional reranker and aggregate queue state. Nonzero exit means a required service is unavailable, failed jobs exist, a question lease expired, object deletion is retrying, or ingestion has been queued for more than five minutes. It never prints credentials, document text or customer question content. Reranker availability is reported as optional because workspaces can disable it; a reranking request still fails closed when its configured provider is unavailable.

Inspect `docker compose logs --since=15m api worker` for errors and structured question timing entries. Logs are development diagnostics, not a configured production alert destination. No email/Slack messages or recurring OS jobs are installed. Production alert routing, dashboards, backup/restore drills and external uptime monitoring are not verified.

## Test isolation and coverage

`make test-db` creates `answerbase_test` if absent. Spring integration tests use this database through the `test` profile and test-specific storage. Application data stays in `answerbase`. Test fixtures clean up their own data; do not point test overrides at a production or customer database. `make test` checks Java, both frontends, connector behavior, and evaluation contracts. JaCoCo combines core unit tests with API integration execution and enforces at least 70% shared-core line coverage with no exclusions. Its HTML report is `backend/coverage/target/site/jacoco-aggregate/index.html`.

Model HTTP responses are bounded while being received, preventing unbounded response-body allocation. HTTP failures and over-limit chunked responses are covered by a local HTTP-server test. This is a response size boundary, not a model-content trust decision.

## Scale probe

After `make backend-check`, run:

```sh
node evals/scale.mjs
```

This command refuses any database other than the fixed local `answerbase_test`. It inserts 2,000 documents with 25 chunks each and deterministic 768-dimensional synthetic vectors inside a transaction, runs permission-filtered vector and keyword query plans, asserts a nonmember cannot read the documents, then rolls back. Failure closes the connection and also rolls back. It does not create original files, use the ingestion queue, invoke models or measure concurrent users. PostgreSQL may retain reusable physical table/index space after rollback; this is a local test database. Do not run it concurrently with integration tests.

Raw plans and a compact report are written under ignored `evals/reports/`. A single query timing is not p50/p95. Shared-core authorization integration tests separately cover cross-organization and group restrictions. This probe only tests the no-membership boundary at scale.

## Manual local acceptance walkthrough

1. Register two distinct customer accounts, confirm their emails in Mailpit, and create separate organizations. Verify each account only sees its own workspaces.
2. As an organization owner, create a workspace, invite a confirmed same-company colleague, and assign viewer/editor/admin as appropriate. Revoke access and verify the former member cannot reopen the workspace.
3. Upload PDF, DOCX, Markdown, HTML and text fixtures. Wait for processing/indexing, replace a file with a new version, and inspect the extracted source plus the original download.
4. Restrict a source to a named group. As a nonmember, verify it is absent from the library, search results and saved source links.
5. Ask an answerable question and an unavailable-data question. Open each citation, inspect its highlighted passage and version, and submit feedback. Review the query log and export unanswered questions as CSV.
6. In the separate staff application, use a provisioned Answerbase account, start a ticket-scoped session and open the real customer application in its delegated frame. Check the target user's permissions and the staff audit trail; end the session and verify the grant stops working.
7. In a disposable workspace, test deletion and inspect its outbox count until physical removal completes. Retention defaults to disabled; enable it only with appropriate customer approval in a real deployment.
8. Run evaluation candidates and review actual answers against evidence. Record misses, latency and reviewer identity. Do not replace unanswered cases or failed runs with fabricated success evidence.

The steps above are an acceptance procedure, not a claim that every step has been performed in the browser for this release. Evidence and limitations are recorded in `prd-checklist.md` and the local results document.
