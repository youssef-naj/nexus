-- Assignment events name the member concerned, inside the same organization (ADR-0004)
ALTER TABLE request_events DROP CONSTRAINT ck_request_events_action;
ALTER TABLE request_events ADD CONSTRAINT ck_request_events_action CHECK (action IN
                                                                          ('SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'ASSIGN', 'UNASSIGN'));

ALTER TABLE request_events ADD COLUMN target_membership_id uuid;
ALTER TABLE request_events ADD CONSTRAINT fk_request_events_target
  FOREIGN KEY (organization_id, target_membership_id)
    REFERENCES memberships (organization_id, id);
-- Assignment events always have a target; workflow transitions never do
ALTER TABLE request_events ADD CONSTRAINT ck_request_events_target_matches_action
  CHECK ((action IN ('ASSIGN', 'UNASSIGN')) = (target_membership_id IS NOT NULL));

-- "Assigned to me": a reviewer's requests and the dashboard count
CREATE INDEX ix_requests_org_assignee_status
  ON service_requests (organization_id, assignee_membership_id, status)
  WHERE assignee_membership_id IS NOT NULL;
