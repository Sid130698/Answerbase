CREATE TABLE answerbase.question_retries (
 question_id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
 available_at timestamptz NOT NULL DEFAULT now() + interval '30 seconds',
 expires_at timestamptz NOT NULL DEFAULT now() + interval '1 day',
 lease_token uuid,
 lease_until timestamptz,
 terminal boolean NOT NULL DEFAULT false,
 FOREIGN KEY (organization_id,workspace_id,question_id)
   REFERENCES answerbase.questions(organization_id,workspace_id,id) ON DELETE CASCADE
);
CREATE INDEX question_retries_due ON answerbase.question_retries(available_at) WHERE NOT terminal;
