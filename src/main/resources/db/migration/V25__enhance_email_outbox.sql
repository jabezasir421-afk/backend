ALTER TABLE email_outbox ADD COLUMN claimed_by VARCHAR(100);
ALTER TABLE email_outbox ADD COLUMN claimed_at TIMESTAMP;

CREATE INDEX email_outbox_claim_idx ON email_outbox (status, claimed_at);
