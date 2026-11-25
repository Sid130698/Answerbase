# Notion export folder connector

Export a Notion space as Markdown or HTML and extract it to a local directory. The connector walks nested folders, uploads PDF/DOCX/MD/HTML/TXT files, skips unsupported assets and hidden files, and refuses symbolic links. It never fetches external URLs embedded in a document.

Use a current **customer access token** with editor/admin membership in the target workspace. Do not use a Supabase service-role key. The API validates the token, organization and workspace access on every request. HTTPS is required unless the API is on loopback.

```sh
# Set ANSWERBASE_TOKEN privately in your terminal; do not commit or paste it into chat.
node connectors/folder-sync.mjs --folder /path/to/extracted-export --workspace WORKSPACE_UUID
# The first command previews changes. To upload:
node connectors/folder-sync.mjs --folder /path/to/extracted-export --workspace WORKSPACE_UUID --apply
```

`ANSWERBASE_API_URL` defaults to `http://localhost:8080/api/v1`. Tokens are never saved by the connector. Manifests are scoped by API origin, authenticated user, workspace and canonical folder, and stored privately under gitignored `.cache/folder-sync/`. Preserve that directory for subsequent syncs. The manifest records content hashes, document/version IDs and a pending idempotency key before an upload. An interrupted upload must be retried with unchanged content to reconcile the server result safely.

Changed files create versions of the same document; unchanged content is skipped. Missing local files are reported and retained remotely—there is no implicit deletion. Renames are treated as new files. This is an incremental import connector, not a bidirectional Notion integration. Run it on demand or via your own local scheduler; no hosted connector service is needed.

Preview limits match the application: 5 MB per file and 200 files per folder (workspace version/byte quotas still apply). A lock prevents simultaneous runs for the same manifest. After a crash, remove a stale `.lock` only after confirming that the old process has stopped.

Validation: `node --test connectors/folder-sync.test.mjs` covers dry run, incremental upload, replacement, retained removals, HTTPS policy and symlink rejection. `node scripts/smoke-folder-sync.mjs` exercises the real local API with the synthetic local test account.
