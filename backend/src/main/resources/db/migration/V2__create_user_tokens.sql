CREATE TABLE user_tokens (
                             id         uuid        PRIMARY KEY,
                             user_id    uuid        NOT NULL,
                             type       varchar(30) NOT NULL,
                             token_hash varchar(64) NOT NULL,
                             expires_at timestamptz NOT NULL,
                             used_at    timestamptz,
                             created_at timestamptz NOT NULL,
                             CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                             CONSTRAINT uq_user_tokens_token_hash UNIQUE (token_hash),
                             CONSTRAINT ck_user_tokens_type CHECK (type IN ('VERIFY_EMAIL', 'PASSWORD_RESET'))
);

-- Invalidating a user's earlier tokens of one type when a new one is issued
CREATE INDEX ix_user_tokens_user_type ON user_tokens (user_id, type);