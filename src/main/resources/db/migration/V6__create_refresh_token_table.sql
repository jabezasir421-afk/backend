CREATE TABLE refresh_token
(
    id              UUID PRIMARY KEY,
    user_account_id UUID         NOT NULL,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_refresh_token_user_account FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE INDEX refresh_token_user_account_idx ON refresh_token (user_account_id);
