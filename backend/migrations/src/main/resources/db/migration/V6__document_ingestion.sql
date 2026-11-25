CREATE TABLE answerbase.documents (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, workspace_id, id),
    FOREIGN KEY (organization_id, workspace_id) REFERENCES answerbase.workspaces(organization_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES answerbase.users(organization_id, id)
);
CREATE TABLE answerbase.document_versions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    document_id uuid NOT NULL,
    version_number integer NOT NULL CHECK (version_number > 0),
    filename varchar(200) NOT NULL,
    media_type varchar(100) NOT NULL,
    byte_size integer NOT NULL CHECK (byte_size > 0 AND byte_size <= 5242880),
    checksum varchar(64) NOT NULL,
    storage_key text NOT NULL UNIQUE,
    request_key uuid NOT NULL,
    created_by uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','processing','processed','failed')),
    error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, version_number),
    UNIQUE (workspace_id, created_by, request_key),
    UNIQUE (organization_id, workspace_id, id),
    FOREIGN KEY (organization_id, workspace_id, document_id) REFERENCES answerbase.documents(organization_id, workspace_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES answerbase.users(organization_id, id)
);
CREATE TABLE answerbase.ingestion_jobs (
    version_id uuid PRIMARY KEY REFERENCES answerbase.document_versions(id),
    state varchar(20) NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','processing','done','failed')),
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz
);
CREATE INDEX ingestion_jobs_claim_idx ON answerbase.ingestion_jobs(state, available_at);
CREATE TABLE answerbase.document_chunks (
    organization_id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    version_id uuid NOT NULL,
    ordinal integer NOT NULL,
    page_number integer,
    start_line integer,
    end_line integer,
    content text NOT NULL,
    PRIMARY KEY (version_id, ordinal),
    FOREIGN KEY (organization_id, workspace_id, version_id) REFERENCES answerbase.document_versions(organization_id, workspace_id, id)
);
CREATE INDEX document_versions_workspace_idx ON answerbase.document_versions(organization_id, workspace_id, created_at);
CREATE INDEX document_chunks_workspace_idx ON answerbase.document_chunks(organization_id, workspace_id, version_id);
