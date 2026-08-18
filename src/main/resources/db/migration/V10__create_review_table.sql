CREATE TABLE review
(
    id          UUID PRIMARY KEY,
    booking_id  UUID      NOT NULL UNIQUE,
    customer_id UUID      NOT NULL,
    worker_id   UUID      NOT NULL,
    rating      SMALLINT  NOT NULL,
    comment     VARCHAR(1000),
    active      BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_review_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_review_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_review_worker FOREIGN KEY (worker_id) REFERENCES worker (id),
    CONSTRAINT chk_review_rating CHECK (rating >= 1 AND rating <= 5)
);

CREATE INDEX review_worker_idx ON review (worker_id) WHERE active = TRUE;
