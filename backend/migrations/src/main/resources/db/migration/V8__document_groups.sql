ALTER TABLE answerbase.documents ADD COLUMN restricted boolean NOT NULL DEFAULT false;
CREATE TABLE answerbase.document_groups (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 name varchar(80) NOT NULL,
 UNIQUE(organization_id,workspace_id,id),
 UNIQUE(workspace_id,name),
 FOREIGN KEY(organization_id,workspace_id) REFERENCES answerbase.workspaces(organization_id,id) ON DELETE CASCADE
);
CREATE TABLE answerbase.group_members (
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 group_id uuid NOT NULL,
 user_id uuid NOT NULL,
 PRIMARY KEY(group_id,user_id),
 FOREIGN KEY(organization_id,workspace_id,group_id) REFERENCES answerbase.document_groups(organization_id,workspace_id,id) ON DELETE CASCADE,
 FOREIGN KEY(organization_id,user_id) REFERENCES answerbase.users(organization_id,id)
);
CREATE TABLE answerbase.document_group_access (
 organization_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 document_id uuid NOT NULL,
 group_id uuid NOT NULL,
 PRIMARY KEY(document_id,group_id),
 FOREIGN KEY(organization_id,workspace_id,document_id) REFERENCES answerbase.documents(organization_id,workspace_id,id) ON DELETE CASCADE,
 FOREIGN KEY(organization_id,workspace_id,group_id) REFERENCES answerbase.document_groups(organization_id,workspace_id,id) ON DELETE CASCADE
);
CREATE INDEX group_members_user_idx ON answerbase.group_members(user_id,workspace_id,group_id);
-- Same predicate applies to metadata, originals, source passages and retrieval candidates.
CREATE FUNCTION answerbase.can_read_document(caller uuid, document uuid) RETURNS boolean
LANGUAGE sql STABLE AS $$
 SELECT EXISTS (
  SELECT 1 FROM answerbase.documents d
  JOIN answerbase.workspace_members m ON m.workspace_id=d.workspace_id AND m.organization_id=d.organization_id AND m.user_id=caller
  JOIN answerbase.users u ON u.id=m.user_id AND u.organization_id=d.organization_id
  WHERE d.id=document AND (NOT d.restricted OR m.role='admin' OR EXISTS (
   SELECT 1 FROM answerbase.document_group_access a JOIN answerbase.group_members g
   ON g.group_id=a.group_id AND g.workspace_id=a.workspace_id AND g.organization_id=a.organization_id
   WHERE a.document_id=d.id AND g.user_id=caller
  ))
 )
$$;
