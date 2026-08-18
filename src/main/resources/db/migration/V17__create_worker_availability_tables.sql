ALTER TABLE worker
    ADD COLUMN online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE';
ALTER TABLE worker
    ADD COLUMN last_seen_at TIMESTAMP;
ALTER TABLE worker
    ADD COLUMN vacation_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE worker
    ADD COLUMN vacation_start DATE;
ALTER TABLE worker
    ADD COLUMN vacation_end DATE;
ALTER TABLE worker
    ADD CONSTRAINT chk_worker_online_status
        CHECK (online_status IN ('ONLINE', 'OFFLINE'));
ALTER TABLE worker
    ADD CONSTRAINT chk_worker_vacation_dates
        CHECK (vacation_mode = FALSE OR (vacation_start IS NOT NULL AND vacation_end IS NOT NULL));

CREATE TABLE worker_working_hours
(
    id          UUID PRIMARY KEY,
    worker_id   UUID      NOT NULL,
    day_of_week SMALLINT  NOT NULL,
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL,
    active      BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_working_hours_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT chk_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_working_hours_time CHECK (start_time < end_time),
    CONSTRAINT uq_worker_day UNIQUE (worker_id, day_of_week)
);

CREATE TABLE worker_schedule_override
(
    id            UUID PRIMARY KEY,
    worker_id     UUID      NOT NULL,
    override_date DATE      NOT NULL,
    available     BOOLEAN   NOT NULL,
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
