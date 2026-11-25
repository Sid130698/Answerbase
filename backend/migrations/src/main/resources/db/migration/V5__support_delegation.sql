CREATE TABLE answerbase.support_grants (
    token_hash varchar(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES answerbase.support_sessions(id),
    target_user_id uuid NOT NULL REFERENCES answerbase.users(id),
    expires_at timestamptz NOT NULL DEFAULT now() + interval '10 minutes',
    revoked_at timestamptz
);
CREATE INDEX support_grants_session_idx ON answerbase.support_grants(session_id);
-- Staff identities and customer identities are mutually exclusive, including inactive staff.
CREATE FUNCTION answerbase.enforce_identity_boundary() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE account_id uuid;
BEGIN
    IF TG_TABLE_NAME='staff_access' THEN account_id:=NEW.user_id; ELSE account_id:=NEW.id; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(account_id::text,0));
    IF TG_TABLE_NAME='staff_access' THEN
        IF EXISTS(SELECT 1 FROM answerbase.users WHERE id=account_id) THEN
            RAISE EXCEPTION 'Customer accounts cannot be Answerbase staff';
        END IF;
    ELSE
        IF EXISTS(SELECT 1 FROM answerbase.staff_access WHERE user_id=account_id) THEN
            RAISE EXCEPTION 'Answerbase staff accounts cannot join customer organizations';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER staff_identity_boundary BEFORE INSERT OR UPDATE ON answerbase.staff_access
FOR EACH ROW EXECUTE FUNCTION answerbase.enforce_identity_boundary();
CREATE TRIGGER customer_identity_boundary BEFORE INSERT OR UPDATE ON answerbase.users
FOR EACH ROW EXECUTE FUNCTION answerbase.enforce_identity_boundary();
