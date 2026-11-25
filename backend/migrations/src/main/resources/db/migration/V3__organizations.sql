CREATE TABLE answerbase.organizations (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL CHECK (length(trim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE answerbase.users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES answerbase.organizations(id),
    organization_role varchar(16) NOT NULL CHECK (organization_role IN ('owner', 'admin', 'member')),
    UNIQUE (organization_id, id)
);

-- Earlier versions had no company identity. Preserve isolated development data;
-- do not silently merge users or infer a company from unverified email domains.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM answerbase.workspace_members m
               JOIN answerbase.workspaces w ON w.id=m.workspace_id
               WHERE m.user_id<>w.created_by) THEN
        RAISE EXCEPTION 'Existing shared workspaces require an explicit organization mapping before V3';
    END IF;
END $$;
INSERT INTO answerbase.organizations (id, name)
SELECT DISTINCT created_by, 'Development organization ' || left(created_by::text, 8)
FROM answerbase.workspaces;
INSERT INTO answerbase.users (id, organization_id, organization_role)
SELECT id, id, 'owner' FROM answerbase.organizations;

ALTER TABLE answerbase.workspaces ADD COLUMN organization_id uuid;
UPDATE answerbase.workspaces SET organization_id = created_by;
ALTER TABLE answerbase.workspaces ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE answerbase.workspaces
    ADD CONSTRAINT workspace_org_fk FOREIGN KEY (organization_id) REFERENCES answerbase.organizations(id),
    ADD CONSTRAINT workspace_creator_org_fk FOREIGN KEY (organization_id, created_by) REFERENCES answerbase.users(organization_id, id),
    ADD CONSTRAINT workspace_org_id_unique UNIQUE (organization_id, id);
CREATE INDEX workspaces_org_idx ON answerbase.workspaces(organization_id);

ALTER TABLE answerbase.workspace_members ADD COLUMN organization_id uuid;
UPDATE answerbase.workspace_members m SET organization_id = w.organization_id
FROM answerbase.workspaces w WHERE w.id = m.workspace_id;
ALTER TABLE answerbase.workspace_members ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE answerbase.workspace_members DROP CONSTRAINT workspace_members_role_check;
UPDATE answerbase.workspace_members SET role='editor' WHERE role='member';
ALTER TABLE answerbase.workspace_members
    ADD CONSTRAINT workspace_members_role_check CHECK (role IN ('admin', 'editor', 'viewer')),
    ADD CONSTRAINT membership_user_org_fk FOREIGN KEY (organization_id, user_id) REFERENCES answerbase.users(organization_id, id),
    ADD CONSTRAINT membership_workspace_org_fk FOREIGN KEY (organization_id, workspace_id) REFERENCES answerbase.workspaces(organization_id, id) ON DELETE CASCADE;
