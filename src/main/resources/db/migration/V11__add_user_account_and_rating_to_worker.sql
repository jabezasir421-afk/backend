ALTER TABLE worker
    ADD COLUMN user_account_id UUID UNIQUE,
    ADD COLUMN average_rating NUMERIC(3, 2) NOT NULL DEFAULT 0,
    ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_worker_user_account FOREIGN KEY (user_account_id) REFERENCES user_account (id),
    ADD CONSTRAINT chk_worker_average_rating CHECK (average_rating >= 0 AND average_rating <= 5),
    ADD CONSTRAINT chk_worker_review_count CHECK (review_count >= 0);
