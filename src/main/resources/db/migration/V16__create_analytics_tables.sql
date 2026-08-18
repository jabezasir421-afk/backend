CREATE TABLE analytics_daily_snapshot
(
    id                 UUID PRIMARY KEY,
    snapshot_date      DATE           NOT NULL UNIQUE,
    total_workers      INTEGER        NOT NULL,
    active_workers     INTEGER        NOT NULL,
    total_customers    INTEGER        NOT NULL,
    active_customers   INTEGER        NOT NULL,
    bookings_created   INTEGER        NOT NULL,
    bookings_completed INTEGER        NOT NULL,
    bookings_cancelled INTEGER        NOT NULL,
    total_revenue      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    avg_rating         NUMERIC(3, 2)  NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL
);

CREATE TABLE analytics_category_ranking
(
    id            UUID PRIMARY KEY,
    snapshot_date DATE      NOT NULL,
    category_id   UUID      NOT NULL,
    booking_count INTEGER   NOT NULL,
    rank          SMALLINT  NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_ranking_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT uq_ranking_date_category UNIQUE (snapshot_date, category_id)
);

CREATE TABLE analytics_worker_ranking
(
    id                 UUID PRIMARY KEY,
    snapshot_date      DATE      NOT NULL,
    worker_id          UUID      NOT NULL,
    completed_bookings INTEGER   NOT NULL,
    rank               SMALLINT  NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    CONSTRAINT fk_ranking_worker FOREIGN KEY (worker_id) REFERENCES worker (id),
    CONSTRAINT uq_ranking_date_worker UNIQUE (snapshot_date, worker_id)
);

CREATE INDEX analytics_snapshot_date_idx ON analytics_daily_snapshot (snapshot_date DESC);
