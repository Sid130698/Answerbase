# Local query admission and recovery

Each model attempt holds a ten-minute database lease and a unique execution token. Publication checks both. An expired or superseded attempt cannot overwrite a newer result, including from its failure handler. A completed idempotent request returns its saved result without generating again. A request using the same key with different content is rejected.

Admission is serialized in a short PostgreSQL transaction, so limits apply across API replicas: one active question per user, three per workspace, four across the installation, and ten attempts per user per minute. These conservative local defaults protect a single model host. Rejected requests return HTTP 429 (or a failure event after an SSE connection opens). These are query limits, not a replacement for perimeter HTTP throttling.

The worker marks expired leases failed every minute and logs an aggregate expiry warning without document/question content. The same request key can then be retried; a stale request cannot publish. Following a process crash, capacity is released after lease expiry, not immediately. Attempts are recorded separately from question rows and are deleted with their question.

Tests simulate an active lease, concurrent request denial, expiration and reuse of the original request ID. Daily workspace budgets are implemented in settings; the release monitor checks retry/lease failures and latency. Provider throughput and actual hosted performance require separate measurement.

## Automatic provider retry

Transient model/network failures persist a retry record in PostgreSQL under the original question ID. The scheduler claims with a lease and execution token, rechecks the user's current workspace/document access, and retries at most three times before the one-day expiry. Provider retries back off two minutes; capacity contention waits one minute without consuming a provider attempt. Permission/other terminal failures stop retrying. Successful publication removes the pending retry.

Recent questions display `queued` while a retry remains eligible and poll for completion. There is no silent cross-provider fallback. The integration test exercises outage, queued retry failure, recovery and reuse of the same idempotent result. Production monitoring reports exhausted retries and expired execution leases.
