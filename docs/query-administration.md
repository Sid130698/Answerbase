# Workspace question administration

Workspace admins can review paginated question history, original answers with supporting quotations, feedback and per-stage timing. The same role check runs for staff delegation; being a support employee does not bypass the selected user's workspace role. Queries from other workspaces cannot be read by guessing their IDs.

The seven-day summary reports completed/failed/abstained requests, input/output tokens, median/p95 latency and known API charges. Unknown hosted prices are counted separately. Zero for local inference means zero API charges, not zero hardware cost.

The unanswered CSV contains the latest 1,000 abstentions, failed requests and answers with thumbs-down feedback. Cells are quoted and dangerous spreadsheet formula prefixes neutralized. The UI states the export limit; the paginated log provides older history.

Admins can save a corrected answer as an evaluation candidate. This preserves the original response and feedback. Candidates are not automatically accepted as trusted gold answers or used to train a model. Human review remains necessary.

Endpoints live under `/api/v1/workspaces/{workspace}`: `GET query-log`, `GET query-log/{id}`, `GET usage`, `GET unanswered.csv`, `PUT query-log/{id}/correction`. Equivalent delegated routes use the existing short-lived grant and support audit trail. Swagger documents these endpoints independently of the customer navigation.

Validation: 19 backend tests pass, including admin/outsider authorization and CSV escaping; frontend lint/build pass. Browser verified unanswered filtering, persisted evaluation candidate, and 390px layout with no page overflow.

Release checks also cover a completed answer entering the unanswered list/CSV after a negative vote, and leaving that list when the vote changes back to positive.
