CREATE TABLE answerbase.workspaces (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL CHECK (length(trim(name)) > 0),
    created_by uuid NOT NULL,
    creation_key uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (created_by, creation_key)
);

CREATE TABLE answerbase.workspace_members (
    workspace_id uuid NOT NULL REFERENCES answerbase.workspaces(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('admin', 'member')),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX workspace_members_user_idx ON answerbase.workspace_members(user_id);
