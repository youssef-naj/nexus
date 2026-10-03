CREATE TABLE audit_logs (
                            id              uuid        PRIMARY KEY,
                            organization_id uuid,
                            actor_user_id   uuid,
                            event_type      varchar(60) NOT NULL,
                            target_type     varchar(40),
                            target_id       uuid,
                            metadata        jsonb       NOT NULL DEFAULT '{}'::jsonb,
                            occurred_at     timestamptz NOT NULL,
                            CONSTRAINT ck_audit_logs_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

-- The organization audit view: newest events of one organization first
CREATE INDEX ix_audit_logs_org_time ON audit_logs (organization_id, occurred_at DESC);

CREATE FUNCTION audit_logs_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();

CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_change();