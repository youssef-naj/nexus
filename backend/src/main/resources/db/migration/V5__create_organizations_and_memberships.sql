CREATE TABLE organizations (
                               id              uuid         PRIMARY KEY,
                               name            varchar(120) NOT NULL,
                               slug            varchar(63)  NOT NULL,
                               status          varchar(20)  NOT NULL DEFAULT 'ACTIVE',
                               request_counter bigint       NOT NULL DEFAULT 0,
                               created_at      timestamptz  NOT NULL,
                               updated_at      timestamptz  NOT NULL,
                               version         bigint       NOT NULL DEFAULT 0,
                               CONSTRAINT uq_organizations_slug UNIQUE (slug),
                               CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
                               CONSTRAINT ck_organizations_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
                               CONSTRAINT ck_organizations_request_counter CHECK (request_counter >= 0)
);

CREATE TABLE memberships (
                             id              uuid        PRIMARY KEY,
                             organization_id uuid        NOT NULL,
                             user_id         uuid        NOT NULL,
                             role            varchar(20) NOT NULL,
                             status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
                             created_at      timestamptz NOT NULL,
                             updated_at      timestamptz NOT NULL,
                             version         bigint      NOT NULL DEFAULT 0,
                             CONSTRAINT fk_memberships_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
                             CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- One membership per user per organization; also the lookup (organization_id, user_id)
                             CONSTRAINT uq_memberships_org_user UNIQUE (organization_id, user_id),
    -- Target for composite foreign keys from tenant tables (ADR-0004)
                             CONSTRAINT uq_memberships_org_id UNIQUE (organization_id, id),
                             CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE')),
                             CONSTRAINT ck_memberships_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

-- "Organizations available to the current user": the unique index above starts with
-- organization_id, so it cannot serve a lookup by user alone.
CREATE INDEX ix_memberships_user ON memberships (user_id);