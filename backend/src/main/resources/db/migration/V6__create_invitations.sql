CREATE TABLE invitations (
                             id                       uuid         PRIMARY KEY,
                             organization_id          uuid         NOT NULL,
                             email                    varchar(320) NOT NULL,
                             role                     varchar(20)  NOT NULL,
                             token_hash               varchar(64)  NOT NULL,
                             status                   varchar(20)  NOT NULL DEFAULT 'PENDING',
                             invited_by_membership_id uuid         NOT NULL,
                             expires_at               timestamptz  NOT NULL,
                             created_at               timestamptz  NOT NULL,
                             decided_at               timestamptz,
                             version                  bigint       NOT NULL DEFAULT 0,
                             CONSTRAINT fk_invitations_organization FOREIGN KEY (organization_id)
                                 REFERENCES organizations (id),
    -- The inviter must belong to the SAME organization (ADR-0004)
                             CONSTRAINT fk_invitations_inviter FOREIGN KEY (organization_id, invited_by_membership_id)
                                 REFERENCES memberships (organization_id, id),
                             CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
                             CONSTRAINT ck_invitations_role CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE')),
                             CONSTRAINT ck_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED')),
                             CONSTRAINT ck_invitations_email_lower CHECK (email = lower(email)),
    -- decided_at is set exactly when the invitation is no longer pending
                             CONSTRAINT ck_invitations_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- At most one pending invitation per person per organization
CREATE UNIQUE INDEX uq_invitations_one_pending
    ON invitations (organization_id, email) WHERE status = 'PENDING';

-- "Pending invitations of this organization, newest first"
CREATE INDEX ix_invitations_org_status ON invitations (organization_id, status, created_at DESC);