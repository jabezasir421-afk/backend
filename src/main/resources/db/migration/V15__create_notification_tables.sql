CREATE TABLE notification
(
    id                UUID PRIMARY KEY,
    recipient_user_id UUID         NOT NULL,
    type              VARCHAR(50)  NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    body              TEXT         NOT NULL,
    reference_type    VARCHAR(50),
    reference_id      UUID,
    read_at           TIMESTAMP,
    email_sent_at     TIMESTAMP,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH')),
    CONSTRAINT chk_notification_type CHECK (type IN (
                                                     'BOOKING_CREATED', 'BOOKING_ACCEPTED', 'BOOKING_REJECTED',
                                                     'BOOKING_STARTED', 'BOOKING_COMPLETED', 'BOOKING_CANCELLED',
                                                     'REVIEW_RECEIVED', 'REVIEW_MODERATED',
                                                     'ACCOUNT_WELCOME', 'ACCOUNT_PASSWORD_CHANGED',
                                                     'ACCOUNT_DEACTIVATED',
                                                     'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED',
                                                     'IDENTITY_DOC_VERIFIED', 'IDENTITY_DOC_REJECTED'
        ))
);

CREATE INDEX notification_recipient_idx ON notification (recipient_user_id, created_at DESC);
CREATE INDEX notification_unread_idx ON notification (recipient_user_id) WHERE read_at IS NULL AND active = TRUE;

CREATE TABLE notification_preference
(
    id                    UUID PRIMARY KEY,
    user_account_id       UUID      NOT NULL UNIQUE,
    email_booking_updates BOOLEAN   NOT NULL DEFAULT TRUE,
    email_account_updates BOOLEAN   NOT NULL DEFAULT TRUE,
    email_review_updates  BOOLEAN   NOT NULL DEFAULT TRUE,
    in_app_enabled        BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    CONSTRAINT fk_pref_user FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE TABLE email_outbox
(
    id              UUID PRIMARY KEY,
    notification_id UUID,
    recipient_email VARCHAR(255) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body_html       TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     SMALLINT     NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_outbox_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX email_outbox_pending_idx ON email_outbox (status, created_at) WHERE status = 'PENDING';
