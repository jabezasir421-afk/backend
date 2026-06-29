ALTER TABLE review
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE review
    ADD COLUMN moderated_by UUID;
ALTER TABLE review
    ADD COLUMN moderated_at TIMESTAMP;
ALTER TABLE review
    ADD COLUMN moderation_notes VARCHAR(500);
ALTER TABLE review
    ADD CONSTRAINT fk_review_moderated_by
        FOREIGN KEY (moderated_by) REFERENCES user_account (id);
ALTER TABLE review
    ADD CONSTRAINT chk_review_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'));

CREATE TABLE review_report
(
    id               UUID PRIMARY KEY,
    review_id        UUID        NOT NULL,
    reporter_user_id UUID        NOT NULL,
    reason           VARCHAR(50) NOT NULL,
    description      VARCHAR(1000),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by      UUID,
    resolved_at      TIMESTAMP,
    resolution_notes VARCHAR(500),
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_report_review FOREIGN KEY (review_id) REFERENCES review (id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES user_account (id),
    CONSTRAINT fk_report_resolver FOREIGN KEY (resolved_by) REFERENCES user_account (id),
    CONSTRAINT chk_report_reason CHECK (reason IN (
                                                   'INAPPROPRIATE', 'SPAM', 'FAKE', 'OFFENSIVE', 'OTHER'
        )),
    CONSTRAINT chk_report_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE UNIQUE INDEX review_report_unique_idx
    ON review_report (review_id, reporter_user_id) WHERE status = 'OPEN';
CREATE INDEX review_moderation_idx ON review (moderation_status) WHERE active = TRUE;
CREATE INDEX review_worker_rating_idx ON review (worker_id, rating) WHERE active = TRUE AND moderation_status = 'APPROVED';
CREATE INDEX booking_analytics_idx ON booking (status, created_at);
