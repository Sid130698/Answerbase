CREATE TABLE answerbase.questions (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 user_id uuid NOT NULL,
 request_key uuid NOT NULL,
 conversation_id uuid NOT NULL,
 question text NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('running','complete','failed')),
 answer jsonb,
 evidence jsonb,
 abstained boolean,
 model text,
 embedding_model text,
 input_tokens bigint NOT NULL DEFAULT 0,
 output_tokens bigint NOT NULL DEFAULT 0,
 cost_usd numeric(16,8),
 trace jsonb,
 error text,
 created_at timestamptz NOT NULL DEFAULT now(),
 completed_at timestamptz,
 UNIQUE(workspace_id,user_id,request_key),
 UNIQUE(organization_id,workspace_id,id),
 FOREIGN KEY(organization_id,workspace_id) REFERENCES answerbase.workspaces(organization_id,id),
 FOREIGN KEY(organization_id,user_id) REFERENCES answerbase.users(organization_id,id)
);
CREATE INDEX questions_history_idx ON answerbase.questions(workspace_id,user_id,conversation_id,created_at DESC);
CREATE TABLE answerbase.answer_feedback (
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 question_id uuid NOT NULL,
 user_id uuid NOT NULL,
 vote smallint NOT NULL CHECK(vote IN (-1,1)),
 reason varchar(30) CHECK(reason IN ('wrong','outdated','incomplete','bad citation')),
 note varchar(2000),
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(question_id,user_id),
 FOREIGN KEY(organization_id,workspace_id,question_id) REFERENCES answerbase.questions(organization_id,workspace_id,id) ON DELETE CASCADE,
 FOREIGN KEY(organization_id,user_id) REFERENCES answerbase.users(organization_id,id)
);
CREATE TABLE answerbase.evaluation_candidates (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 question_id uuid NOT NULL,
 corrected_answer text NOT NULL,
 created_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(question_id),
 FOREIGN KEY(organization_id,workspace_id,question_id) REFERENCES answerbase.questions(organization_id,workspace_id,id) ON DELETE CASCADE,
 FOREIGN KEY(organization_id,created_by) REFERENCES answerbase.users(organization_id,id)
);
