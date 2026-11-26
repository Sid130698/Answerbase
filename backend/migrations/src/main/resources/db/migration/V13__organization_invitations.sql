ALTER TABLE answerbase.users ADD COLUMN email varchar(320);
CREATE TABLE answerbase.organization_invitations (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL REFERENCES answerbase.organizations(id),
 workspace_id uuid NOT NULL,
 email varchar(320) NOT NULL,
 workspace_role varchar(20) NOT NULL CHECK(workspace_role IN ('admin','editor','viewer')),
 token_hash char(64) NOT NULL UNIQUE,
 invited_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '7 days',
 accepted_by uuid,
 accepted_at timestamptz,
 revoked_at timestamptz,
 FOREIGN KEY(organization_id,workspace_id) REFERENCES answerbase.workspaces(organization_id,id) ON DELETE CASCADE,
 FOREIGN KEY(organization_id,invited_by) REFERENCES answerbase.users(organization_id,id)
);
