ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS password_reset_token_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS password_reset_token_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_user_account_reset_token_hash ON user_account (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;
