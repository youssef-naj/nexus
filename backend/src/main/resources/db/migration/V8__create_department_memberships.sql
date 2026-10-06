CREATE TABLE department_memberships (
                                      organization_id uuid        NOT NULL,
                                      department_id   uuid        NOT NULL,
                                      membership_id   uuid        NOT NULL,
                                      created_at      timestamptz NOT NULL,
  -- A member is in a department at most once; also serves "who is in this department?"
                                      CONSTRAINT pk_department_memberships PRIMARY KEY (department_id, membership_id),
  -- Both sides must belong to the SAME organization as the row itself (ADR-0004)
                                      CONSTRAINT fk_dm_department FOREIGN KEY (organization_id, department_id)
                                        REFERENCES departments (organization_id, id),
                                      CONSTRAINT fk_dm_membership FOREIGN KEY (organization_id, membership_id)
                                        REFERENCES memberships (organization_id, id)
);

-- "Which departments is this member in?" The primary key starts with department_id,
-- so it cannot serve a lookup by member alone.
CREATE INDEX ix_dm_membership ON department_memberships (membership_id);
