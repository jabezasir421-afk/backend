-- V26: Migration hardening, foreign key indexes, and business invariants

-- 1. Frequently queried foreign key indexes
CREATE INDEX IF NOT EXISTS idx_booking_category_id
    ON booking (category_id);

CREATE INDEX IF NOT EXISTS idx_booking_address_id
    ON booking (address_id);

CREATE INDEX IF NOT EXISTS idx_booking_worker_scheduled_status
    ON booking (worker_id, scheduled_date, status);

CREATE INDEX IF NOT EXISTS idx_worker_category_id
    ON worker (category_id);

CREATE INDEX IF NOT EXISTS idx_worker_profile_photo
    ON worker (profile_photo_file_id) WHERE profile_photo_file_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_review_customer_id
    ON review (customer_id);

CREATE INDEX IF NOT EXISTS idx_review_worker_status_active
    ON review (worker_id, moderation_status, active);

-- 2. Business invariant constraints
ALTER TABLE customer
    ADD CONSTRAINT chk_customer_name
        CHECK (TRIM(first_name) <> '' AND TRIM(last_name) <> '');

ALTER TABLE worker
    ADD CONSTRAINT chk_worker_name
        CHECK (TRIM(first_name) <> '' AND TRIM(last_name) <> '');

ALTER TABLE user_account
    ADD CONSTRAINT chk_user_account_role
        CHECK (role IN ('CUSTOMER', 'WORKER', 'ADMIN'));

ALTER TABLE booking
    ADD CONSTRAINT chk_booking_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
