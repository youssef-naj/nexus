CREATE TABLE request_events (
                              id                uuid         PRIMARY KEY,
                              organization_id   uuid         NOT NULL,
                              request_id        uuid         NOT NULL,
                              actor_membership_id uuid       NOT NULL,
                              action            varchar(30)  NOT NULL,
                              from_status       varchar(30)  NOT NULL,
                              to_status         varchar(30)  NOT NULL,
                              comment           varchar(1000),
                              occurred_at       timestamptz  NOT NULL,
  -- The request and the actor must both belong to the event's organization (ADR-0004)
                              CONSTRAINT fk_request_events_request FOREIGN KEY (organization_id, request_id)
                                REFERENCES service_requests (organization_id, id),
                              CONSTRAINT fk_request_events_actor FOREIGN KEY (organization_id, actor_membership_id)
                                REFERENCES memberships (organization_id, id),
                              CONSTRAINT ck_request_events_action CHECK (action IN
                                                                         ('SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES')),
                              CONSTRAINT ck_request_events_from_status CHECK (from_status IN
                                                                              ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
                              CONSTRAINT ck_request_events_to_status CHECK (to_status IN
                                                                            ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
                              CONSTRAINT ck_request_events_comment_not_blank CHECK (comment IS NULL OR length(btrim(comment)) > 0)
);

-- A request's history, oldest first
CREATE INDEX ix_request_events_request_time
  ON request_events (organization_id, request_id, occurred_at);

-- Append-only, like the audit log: history is never rewritten
CREATE FUNCTION request_events_reject_change() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'request_events is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_request_events_no_update_delete
  BEFORE UPDATE OR DELETE ON request_events
  FOR EACH ROW EXECUTE FUNCTION request_events_reject_change();

CREATE TRIGGER trg_request_events_no_truncate
  BEFORE TRUNCATE ON request_events
  FOR EACH STATEMENT EXECUTE FUNCTION request_events_reject_change();
