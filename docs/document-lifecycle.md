# Document deletion, retention and orphan cleanup

Editors/admins with access to a document can explicitly delete it. The customer UI requires typing DELETE. Deletion removes all versions, extracted text, chunks/embeddings, permission-group links, and saved answers containing the document (including associated feedback/corrections). Active questions in the workspace are cancelled. These effects are permanent and are explained before confirmation.

The metadata transaction first locks the workspace, then removes ingestion and indexing jobs. Original-file keys enter a durable deletion outbox before metadata is erased. The worker deletes private files idempotently and retries storage failures with increasing delay capped at one hour. Original bytes may remain in private storage until the worker succeeds, but application access is removed immediately. Workspace admins can see pending/retrying deletion counts under Access.

Answer publication takes a shared lock on the same workspace and rechecks source access. Together with execution-token fencing and removal of active question rows, this prevents in-flight generation from restoring deleted evidence. Ingestion/indexing publication checks its queue lease; removed jobs cannot republish chunks.

## Retention

Both policies default to **disabled (0)**. Workspace admins can set 1–3650 days and must acknowledge permanent deletion in the UI. Hourly maintenance deletes documents whose newest version is older than the selected age, including their older versions. The condition is rechecked under the workspace lock so a concurrent replacement protects the document. Question retention removes old questions and their feedback, corrections and attempt records.

## Local orphan reconciliation

Daily maintenance scans generated local object paths older than seven days. It skips symbolic links, busy workspace uploads and objects still referenced by a document version. Unreferenced originals and abandoned upload temp files enter the same durable deletion outbox. The seven-day guard deliberately retains recent crash leftovers. This scan is local-volume specific; remote storage inventory reconciliation is not implemented, and no hosted storage account is configured.

The worker now needs write access to the private document volume. Do not change storage provider/root while deletion jobs remain pending; keys identify objects in the currently configured backend. Backups and infrastructure snapshots are outside application deletion and require a separate operational retention policy.

## Verification

Backend checks cover permission denial, removal of chunks and saved evidence, physical deletion, expired-document retention, retry after a simulated storage outage, orphan cleanup and preservation of a referenced old object. Frontend lint/build pass. Automated fixtures are disposable; no existing customer documents were deleted during validation.
