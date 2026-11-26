CREATE TABLE answerbase.workspace_settings (
 workspace_id uuid PRIMARY KEY REFERENCES answerbase.workspaces(id) ON DELETE CASCADE,
 retrieval_mode varchar(20) NOT NULL CHECK(retrieval_mode IN ('hybrid','vector','keyword')),
 candidates integer NOT NULL CHECK(candidates BETWEEN 1 AND 100),
 top_k integer NOT NULL CHECK(top_k BETWEEN 1 AND 20 AND top_k<=candidates),
 rerank boolean NOT NULL,
 generation_model varchar(200) NOT NULL DEFAULT 'default',
 chunk_chars integer NOT NULL DEFAULT 1800 CHECK(chunk_chars BETWEEN 600 AND 4000),
 context_line boolean NOT NULL DEFAULT true,
 redact_pii boolean NOT NULL DEFAULT true,
 daily_attempts integer NOT NULL DEFAULT 100 CHECK(daily_attempts BETWEEN 1 AND 10000)
);
