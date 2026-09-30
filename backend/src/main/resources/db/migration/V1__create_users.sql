CREATE TABLE users (
                       id                uuid         PRIMARY KEY,
                       email             varchar(320) NOT NULL,
                       password_hash     varchar(255) NOT NULL,
                       display_name      varchar(120) NOT NULL,
                       status            varchar(20)  NOT NULL DEFAULT 'ACTIVE',
                       email_verified_at timestamptz,
                       platform_role     varchar(30),
                       created_at        timestamptz  NOT NULL,
                       updated_at        timestamptz  NOT NULL,
                       version           bigint       NOT NULL DEFAULT 0,
                       CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
                       CONSTRAINT ck_users_platform_role CHECK (platform_role IS NULL OR platform_role IN ('PLATFORM_ADMIN'))
);

-- Case-insensitive uniqueness: Ada@x.com and ada@x.com are the same account.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));