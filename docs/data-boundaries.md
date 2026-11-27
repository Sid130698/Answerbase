# Data boundaries for the pilot

This describes the implemented deployment, not a certification. Hosted settings must be checked in the actual accounts before importing client documents.

| Boundary | Data processed | Controls and recovery |
| --- | --- | --- |
| Customer/staff browsers → Supabase Auth | Login credentials, confirmation/reset flow, identity session | Password handling stays with Auth. Java verifies signed tokens against the configured issuer/JWKS; workspace access is application-owned. Staff grants are separately provisioned, never inferred from a client organization. |
| Browser → Java API → VM PostgreSQL | Organization/workspace membership, extracted document text, embeddings, questions, answers, feedback, audit records | Membership and document-group filtering before retrieval; current authorization rechecked during streaming/publication. Database has no public port. Daily backup command verifies restoration in a temporary database. |
| API/worker → private Supabase Storage | Original uploaded files under server-generated keys | Server credentials only; no browser bucket policy. Original downloads pass application authorization. Deletion uses a retrying outbox; old unreferenced objects are reconciled. Full application backups include referenced originals. |
| API → local embeddings/reranker | Search text and authorized retrieved passages | Runs on the VM; no hosted embedding/reranking API. These services have no public ports. |
| API → Groq | Question, short conversation context and selected evidence for generation | Limited email/phone/US-SSN masking is available. This is not comprehensive PII detection: other identifiers and confidential prose can remain. Do not submit documents without authorization to use the provider. |
| Supabase → SMTP provider | Recipient email, confirmation/reset links and message content | Verify sender/domain, exact redirect origins and actual delivery. SMTP receives authentication mail, not the document corpus. |
| Monitor → optional operator webhook | Aggregate health, counts and timing alerts | No question text or user identities in monitor payloads. Webhook is optional; without configuration, alerts are local logs only. |

Answerbase does not train or fine-tune models on customer documents. Local evaluation fixtures are synthetic or attributed public material. Provider handling is a separate policy: Groq documents default non-retention of inference content with reliability/abuse exceptions, and account-level Zero Data Retention controls. Enable and verify the intended setting in the actual account; the application cannot certify that setting. [Groq data controls](https://console.groq.com/docs/your-data).

Public traffic uses HTTPS through Caddy. Inter-container traffic stays on the VM's private Docker network. Original files, extracted text, database dumps and local model caches are not encrypted by this application at rest; configure and verify the host/provider storage encryption and operator access. `.env.production` and backups use private filesystem permissions, which are access restrictions rather than encryption. Backup copies leaving the VM must be encrypted and stored under an operator-controlled account.

Supabase Auth is separate from the application database. An application dump cannot restore lost authentication identities or signing settings. Supabase database backups also do not include stored object contents; the supplied full backup retrieves originals separately. Arrange Auth recovery and a complete off-host restore exercise before using real customer data. [Supabase backup scope](https://supabase.com/docs/guides/platform/backups).

Retention is explicitly configured per workspace. Deletion removes live source/derived records and queues original removal; it does not erase earlier backup copies. Choose a backup retention window, remove expired copies, and record that policy before a client pilot. Never put credentials, customer backup dumps, completed reviewer identities or real tester feedback in the public repository.
