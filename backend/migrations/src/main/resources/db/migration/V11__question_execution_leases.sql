ALTER TABLE answerbase.questions ADD COLUMN execution_token uuid;
ALTER TABLE answerbase.questions ADD COLUMN lease_until timestamptz;
UPDATE answerbase.questions SET status='failed',error='Request interrupted by upgrade. Retry the question.',completed_at=now() WHERE status='running';
CREATE TABLE answerbase.question_attempts (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 question_id uuid NOT NULL,
 user_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(organization_id,workspace_id,question_id) REFERENCES answerbase.questions(organization_id,workspace_id,id) ON DELETE CASCADE
);
CREATE INDEX question_attempts_user_time ON answerbase.question_attempts(user_id,created_at DESC);
CREATE INDEX questions_active_workspace ON answerbase.questions(workspace_id,lease_until) WHERE status='running';
