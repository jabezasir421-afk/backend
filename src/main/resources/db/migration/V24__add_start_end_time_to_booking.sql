ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time TIME;

UPDATE booking
SET start_time = CAST(SUBSTRING(time_slot FROM 1 FOR 5) AS TIME),
    end_time = CAST(SUBSTRING(time_slot FROM 7 FOR 5) AS TIME)
WHERE start_time IS NULL AND time_slot IS NOT NULL AND time_slot LIKE '%-%';

ALTER TABLE booking
    ADD CONSTRAINT chk_booking_start_end_time CHECK (start_time IS NULL OR end_time IS NULL OR start_time < end_time);

CREATE INDEX IF NOT EXISTS idx_booking_worker_scheduled_interval
    ON booking (worker_id, scheduled_date, start_time, end_time);
