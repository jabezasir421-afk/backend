CREATE INDEX worker_search_idx ON worker (
                                          category_id, active, verified, available, average_rating, hourly_rate
    ) WHERE active = TRUE;

CREATE INDEX worker_experience_idx ON worker (experience_years) WHERE active = TRUE;

CREATE TABLE worker_service_area
(
    id         UUID PRIMARY KEY,
    worker_id  UUID         NOT NULL,
    city       VARCHAR(100) NOT NULL,
    state      VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_service_area_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX worker_service_area_unique_idx
    ON worker_service_area (worker_id, LOWER(city), LOWER(state)) WHERE active = TRUE;
CREATE INDEX worker_service_area_city_idx ON worker_service_area (LOWER(city), LOWER(state)) WHERE active = TRUE;
