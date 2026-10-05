CREATE TABLE departments (
                           id              uuid         PRIMARY KEY,
                           organization_id uuid         NOT NULL,
                           name            varchar(80)  NOT NULL,
                           description     varchar(500),
                           active          boolean      NOT NULL DEFAULT true,
                           created_at      timestamptz  NOT NULL,
                           updated_at      timestamptz  NOT NULL,
                           version         bigint       NOT NULL DEFAULT 0,
                           CONSTRAINT fk_departments_organization FOREIGN KEY (organization_id)
                             REFERENCES organizations (id),
  -- Target for composite foreign keys from tenant tables (ADR-0004)
                           CONSTRAINT uq_departments_org_id UNIQUE (organization_id, id),
                           CONSTRAINT ck_departments_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- Unique names per organization, ignoring case, including deactivated departments.
-- Also serves the listing, which sorts one organization's departments by name.
CREATE UNIQUE INDEX uq_departments_org_name ON departments (organization_id, lower(name));
