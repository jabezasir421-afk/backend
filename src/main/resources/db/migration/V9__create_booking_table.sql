CREATE TABLE booking
(
    id                  UUID PRIMARY KEY,
    customer_id         UUID           NOT NULL,
    worker_id           UUID           NOT NULL,
    category_id         UUID           NOT NULL,
    address_id          UUID           NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    scheduled_date      DATE           NOT NULL,
    time_slot           VARCHAR(20)    NOT NULL,
    description         TEXT           NOT NULL,
    quoted_amount       NUMERIC(10, 2) NOT NULL,
    final_amount        NUMERIC(10, 2),
    cancellation_reason VARCHAR(500),
    cancelled_by        UUID,
    accepted_at         TIMESTAMP,
    completed_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    address_line1       VARCHAR(255)   NOT NULL,
    address_city        VARCHAR(100)   NOT NULL,
    address_state       VARCHAR(100)   NOT NULL,
    address_pincode     VARCHAR(6)     NOT NULL,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_booking_worker FOREIGN KEY (worker_id) REFERENCES worker (id),
    CONSTRAINT fk_booking_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_booking_address FOREIGN KEY (address_id) REFERENCES address (id),
    CONSTRAINT fk_booking_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES user_account (id),
    CONSTRAINT chk_booking_quoted_amount CHECK (quoted_amount > 0),
    CONSTRAINT chk_booking_final_amount CHECK (final_amount IS NULL OR final_amount > 0)
);

CREATE INDEX booking_customer_status_idx ON booking (customer_id, status);
CREATE INDEX booking_worker_status_idx ON booking (worker_id, status);
CREATE INDEX booking_scheduled_date_idx ON booking (scheduled_date);
CREATE INDEX booking_status_created_at_idx ON booking (status, created_at);
