CREATE TABLE answerbase.workspace_retention (
 workspace_id uuid PRIMARY KEY REFERENCES answerbase.workspaces(id) ON DELETE CASCADE,
 document_days integer NOT NULL DEFAULT 0 CHECK(document_days BETWEEN 0 AND 3650),
 question_days integer NOT NULL DEFAULT 0 CHECK(question_days BETWEEN 0 AND 3650)
);
CREATE TABLE answerbase.object_deletions (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 storage_key text NOT NULL UNIQUE,
 attempts integer NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,
 lease_until timestamptz,
 error text,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX object_deletions_ready ON answerbase.object_deletions(available_at);
