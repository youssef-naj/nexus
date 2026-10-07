CREATE TABLE service_requests (
                                id                       uuid         PRIMARY KEY,
                                organization_id          uuid         NOT NULL,
                                reference                varchar(20)  NOT NULL,
                                title                    varchar(150) NOT NULL,
                                description              varchar(5000),
                                category                 varchar(30)  NOT NULL,
                                status                   varchar(30)  NOT NULL DEFAULT 'DRAFT',
                                created_by_membership_id uuid         NOT NULL,
                                assignee_membership_id   uuid,
                                department_id            uuid,
                                due_date                 date,
                                created_at               timestamptz  NOT NULL,
                                updated_at               timestamptz  NOT NULL,
                                version                  bigint       NOT NULL DEFAULT 0,
                                CONSTRAINT fk_requests_organization FOREIGN KEY (organization_id)
                                  REFERENCES organizations (id),
  -- Everything a request points at must belong to the SAME organization (ADR-0004).
  -- With a NULL assignee or department the key is skipped, which is what "optional" means.
                                CONSTRAINT fk_requests_creator FOREIGN KEY (organization_id, created_by_membership_id)
                                  REFERENCES memberships (organization_id, id),
                                CONSTRAINT fk_requests_assignee FOREIGN KEY (organization_id, assignee_membership_id)
                                  REFERENCES memberships (organization_id, id),
                                CONSTRAINT fk_requests_department FOREIGN KEY (organization_id, department_id)
                                  REFERENCES departments (organization_id, id),
                                CONSTRAINT uq_requests_org_reference UNIQUE (organization_id, reference),
  -- Target for the composite foreign key of request events (Phase 6)
                                CONSTRAINT uq_requests_org_id UNIQUE (organization_id, id),
                                CONSTRAINT ck_requests_category CHECK (category IN
                                                                       ('IT_SUPPORT', 'FACILITIES', 'HR', 'FINANCE', 'OTHER')),
                                CONSTRAINT ck_requests_status CHECK (status IN
                                                                     ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
                                CONSTRAINT ck_requests_title_not_blank CHECK (length(btrim(title)) > 0)
);

-- The request list and dashboard counts: one organization, filtered by status, newest first
CREATE INDEX ix_requests_org_status_created
  ON service_requests (organization_id, status, created_at DESC);

-- "My requests", and the employee's view of the list
CREATE INDEX ix_requests_org_creator_created
  ON service_requests (organization_id, created_by_membership_id, created_at DESC);
