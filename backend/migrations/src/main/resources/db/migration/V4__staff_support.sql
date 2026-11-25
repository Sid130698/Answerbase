CREATE TABLE answerbase.staff_access (
    user_id uuid PRIMARY KEY,
    role varchar(24) NOT NULL CHECK (role IN ('support', 'platform_admin')),
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE answerbase.support_sessions (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES answerbase.staff_access(user_id),
    organization_id uuid NOT NULL REFERENCES answerbase.organizations(id),
    reason varchar(500) NOT NULL CHECK (length(trim(reason)) >= 5),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT now() + interval '30 minutes',
    closed_at timestamptz
);
CREATE INDEX support_sessions_actor_idx ON answerbase.support_sessions(actor_id, expires_at);
CREATE TABLE answerbase.support_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id uuid NOT NULL,
    session_id uuid,
    organization_id uuid,
    target_user_id uuid,
    action varchar(80) NOT NULL,
    detail text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX support_audit_actor_idx ON answerbase.support_audit(actor_id, id DESC);
CREATE FUNCTION answerbase.reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Support audit records are append-only'; END $$;
CREATE TRIGGER support_audit_immutable BEFORE UPDATE OR DELETE ON answerbase.support_audit
FOR EACH ROW EXECUTE FUNCTION answerbase.reject_audit_mutation();
