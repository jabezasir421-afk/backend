# Phase 3 Flyway Migrations (V12–V20)

Reference SQL for Phase 3. Apply as separate migration files in `src/main/resources/db/migration/` when implementing.

**Conventions:** UUID PKs, `created_at`/`updated_at` TIMESTAMP NOT NULL, partial indexes with `WHERE`, CHECK constraints, case-insensitive uniqueness via `LOWER()`.

---

## V12 — create_stored_file_table

```sql
CREATE TABLE stored_file (
    id              UUID PRIMARY KEY,
    owner_user_id   UUID NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID,
    file_category   VARCHAR(30) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_stored_file_owner FOREIGN KEY (owner_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_stored_file_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT chk_stored_file_category CHECK (file_category IN (
        'PROFILE_PHOTO', 'PORTFOLIO_IMAGE', 'CERTIFICATE', 'IDENTITY_DOC'
    ))
);

CREATE UNIQUE INDEX stored_file_storage_key_idx ON stored_file (storage_key);
CREATE INDEX stored_file_entity_idx ON stored_file (entity_type, entity_id) WHERE active = TRUE;
CREATE INDEX stored_file_owner_idx ON stored_file (owner_user_id);
```

---

## V13 — create_worker_portfolio_tables

```sql
ALTER TABLE worker ADD COLUMN primary_city VARCHAR(100);
ALTER TABLE worker ADD COLUMN primary_state VARCHAR(100);
ALTER TABLE worker ADD COLUMN profile_photo_file_id UUID;
ALTER TABLE worker ADD COLUMN profile_completion_percent SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE worker ADD CONSTRAINT fk_worker_profile_photo
    FOREIGN KEY (profile_photo_file_id) REFERENCES stored_file (id);
ALTER TABLE worker ADD CONSTRAINT chk_worker_profile_completion
    CHECK (profile_completion_percent >= 0 AND profile_completion_percent <= 100);

CREATE TABLE worker_portfolio_item (
    id            UUID PRIMARY KEY,
    worker_id     UUID NOT NULL,
    file_id       UUID NOT NULL,
    title         VARCHAR(200),
    description   VARCHAR(1000),
    display_order SMALLINT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_portfolio_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT uq_portfolio_file UNIQUE (file_id)
);

CREATE TABLE worker_certificate (
    id                  UUID PRIMARY KEY,
    worker_id           UUID NOT NULL,
    file_id             UUID NOT NULL,
    title               VARCHAR(200) NOT NULL,
    issuing_org         VARCHAR(200),
    issue_date          DATE,
    expiry_date         DATE,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_certificate_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_certificate_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT chk_certificate_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE worker_identity_document (
    id                  UUID PRIMARY KEY,
    worker_id           UUID NOT NULL,
    file_id             UUID NOT NULL,
    document_type       VARCHAR(30) NOT NULL,
    document_number     VARCHAR(50),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_by         UUID,
    verified_at         TIMESTAMP,
    rejection_reason    VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_identity_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_identity_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT fk_identity_verified_by FOREIGN KEY (verified_by) REFERENCES user_account (id),
    CONSTRAINT chk_identity_type CHECK (document_type IN ('AADHAAR', 'PAN', 'DRIVING_LICENSE', 'OTHER')),
    CONSTRAINT chk_identity_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX worker_primary_city_idx ON worker (LOWER(primary_city)) WHERE active = TRUE;
CREATE INDEX portfolio_worker_idx ON worker_portfolio_item (worker_id) WHERE active = TRUE;
```

---

## V14 — create_search_support_indexes

```sql
CREATE INDEX worker_search_idx ON worker (
    category_id, active, verified, available, average_rating, hourly_rate
) WHERE active = TRUE;

CREATE INDEX worker_experience_idx ON worker (experience_years) WHERE active = TRUE;

CREATE TABLE worker_service_area (
    id          UUID PRIMARY KEY,
    worker_id   UUID NOT NULL,
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_service_area_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX worker_service_area_unique_idx
    ON worker_service_area (worker_id, LOWER(city), LOWER(state)) WHERE active = TRUE;
CREATE INDEX worker_service_area_city_idx ON worker_service_area (LOWER(city), LOWER(state)) WHERE active = TRUE;
```

---

## V15 — create_notification_tables

```sql
CREATE TABLE notification (
    id                UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type              VARCHAR(50) NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    body              TEXT NOT NULL,
    reference_type    VARCHAR(50),
    reference_id      UUID,
    read_at           TIMESTAMP,
    email_sent_at     TIMESTAMP,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH')),
    CONSTRAINT chk_notification_type CHECK (type IN (
        'BOOKING_CREATED', 'BOOKING_ACCEPTED', 'BOOKING_REJECTED',
        'BOOKING_STARTED', 'BOOKING_COMPLETED', 'BOOKING_CANCELLED',
        'REVIEW_RECEIVED', 'REVIEW_MODERATED',
        'ACCOUNT_WELCOME', 'ACCOUNT_PASSWORD_CHANGED', 'ACCOUNT_DEACTIVATED',
        'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED',
        'IDENTITY_DOC_VERIFIED', 'IDENTITY_DOC_REJECTED'
    ))
);

CREATE INDEX notification_recipient_idx ON notification (recipient_user_id, created_at DESC);
CREATE INDEX notification_unread_idx ON notification (recipient_user_id) WHERE read_at IS NULL AND active = TRUE;

CREATE TABLE notification_preference (
    id                    UUID PRIMARY KEY,
    user_account_id       UUID NOT NULL UNIQUE,
    email_booking_updates BOOLEAN NOT NULL DEFAULT TRUE,
    email_account_updates BOOLEAN NOT NULL DEFAULT TRUE,
    email_review_updates  BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    CONSTRAINT fk_pref_user FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE TABLE email_outbox (
    id              UUID PRIMARY KEY,
    notification_id UUID,
    recipient_email VARCHAR(255) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body_html         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     SMALLINT NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_outbox_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX email_outbox_pending_idx ON email_outbox (status, created_at) WHERE status = 'PENDING';
```

---

## V17 — create_analytics_tables

```sql
CREATE TABLE analytics_daily_snapshot (
    id                 UUID PRIMARY KEY,
    snapshot_date      DATE NOT NULL UNIQUE,
    total_workers      INTEGER NOT NULL,
    active_workers     INTEGER NOT NULL,
    total_customers    INTEGER NOT NULL,
    active_customers   INTEGER NOT NULL,
    bookings_created   INTEGER NOT NULL,
    bookings_completed INTEGER NOT NULL,
    bookings_cancelled INTEGER NOT NULL,
    total_revenue      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    avg_rating         NUMERIC(3, 2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);

CREATE TABLE analytics_category_ranking (
    id            UUID PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    category_id   UUID NOT NULL,
    booking_count INTEGER NOT NULL,
    rank          SMALLINT NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_ranking_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT uq_ranking_date_category UNIQUE (snapshot_date, category_id)
);

CREATE TABLE analytics_worker_ranking (
    id                 UUID PRIMARY KEY,
    snapshot_date      DATE NOT NULL,
    worker_id          UUID NOT NULL,
    completed_bookings INTEGER NOT NULL,
    rank               SMALLINT NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    CONSTRAINT fk_ranking_worker FOREIGN KEY (worker_id) REFERENCES worker (id),
    CONSTRAINT uq_ranking_date_worker UNIQUE (snapshot_date, worker_id)
);

CREATE INDEX analytics_snapshot_date_idx ON analytics_daily_snapshot (snapshot_date DESC);
```

---

## V18 — create_worker_availability_tables

```sql
ALTER TABLE worker ADD COLUMN online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE';
ALTER TABLE worker ADD COLUMN last_seen_at TIMESTAMP;
ALTER TABLE worker ADD COLUMN vacation_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE worker ADD COLUMN vacation_start DATE;
ALTER TABLE worker ADD COLUMN vacation_end DATE;
ALTER TABLE worker ADD CONSTRAINT chk_worker_online_status
    CHECK (online_status IN ('ONLINE', 'OFFLINE'));
ALTER TABLE worker ADD CONSTRAINT chk_worker_vacation_dates
    CHECK (vacation_mode = FALSE OR (vacation_start IS NOT NULL AND vacation_end IS NOT NULL));

CREATE TABLE worker_working_hours (
    id          UUID PRIMARY KEY,
    worker_id   UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_working_hours_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT chk_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_working_hours_time CHECK (start_time < end_time),
    CONSTRAINT uq_worker_day UNIQUE (worker_id, day_of_week)
);

CREATE TABLE worker_schedule_override (
    id            UUID PRIMARY KEY,
    worker_id     UUID NOT NULL,
    override_date DATE NOT NULL,
    available     BOOLEAN NOT NULL,
    start_time    TIME,
    end_time      TIME,
    reason        VARCHAR(200),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_schedule_override_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT uq_worker_override_date UNIQUE (worker_id, override_date)
);

CREATE INDEX worker_online_idx ON worker (online_status) WHERE active = TRUE AND available = TRUE;
CREATE INDEX schedule_override_date_idx ON worker_schedule_override (override_date, worker_id);
CREATE INDEX booking_availability_idx ON booking (worker_id, scheduled_date, status);
```

---

## V19 — extend_review_moderation

```sql
ALTER TABLE review ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE review ADD COLUMN moderated_by UUID;
ALTER TABLE review ADD COLUMN moderated_at TIMESTAMP;
ALTER TABLE review ADD COLUMN moderation_notes VARCHAR(500);
ALTER TABLE review ADD CONSTRAINT fk_review_moderated_by
    FOREIGN KEY (moderated_by) REFERENCES user_account (id);
ALTER TABLE review ADD CONSTRAINT chk_review_moderation_status
    CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'));

CREATE TABLE review_report (
    id               UUID PRIMARY KEY,
    review_id        UUID NOT NULL,
    reporter_user_id UUID NOT NULL,
    reason           VARCHAR(50) NOT NULL,
    description      VARCHAR(1000),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by      UUID,
    resolved_at      TIMESTAMP,
    resolution_notes VARCHAR(500),
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
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
CREATE INDEX review_worker_rating_idx ON review (worker_id, rating)
    WHERE active = TRUE AND moderation_status = 'APPROVED';
CREATE INDEX booking_analytics_idx ON booking (status, created_at);
```

---

## V20 — create_audit_log_table

```sql
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY,
    actor_user_id   UUID,
    actor_role      VARCHAR(30),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX audit_log_created_idx ON audit_log (created_at DESC);
CREATE INDEX audit_log_correlation_idx ON audit_log (correlation_id);
```

---

## Migration File Mapping

| File | Sprint |
|------|--------|
| `V12__create_stored_file_table.sql` | 3.1 |
| `V20__create_audit_log_table.sql` | 3.1 |
| `V13__create_worker_portfolio_tables.sql` | 3.2 |
| `V18__create_worker_availability_tables.sql` | 3.3 |
| `V14__create_search_support_indexes.sql` | 3.4 |
| `V19__extend_review_moderation.sql` | 3.5 |
| `V15__create_notification_tables.sql` | 3.6 |
| `V17__create_analytics_tables.sql` | 3.8 |

Note: V16 is unused; V17–V20 order can be renumbered at implementation time to maintain sequential Flyway versions.
