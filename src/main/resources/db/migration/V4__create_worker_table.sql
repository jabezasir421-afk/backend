CREATE TABLE worker
(
    id               UUID PRIMARY KEY,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    phone_number     VARCHAR(30)  NOT NULL UNIQUE,
    email            VARCHAR(255) UNIQUE,
    gender           VARCHAR(30),
    date_of_birth    DATE,
    experience_years INTEGER,
    bio              VARCHAR(1000),
    hourly_rate      NUMERIC(10, 2),
    available        BOOLEAN      NOT NULL DEFAULT TRUE,
    verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    category_id      UUID         NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT fk_worker_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT chk_worker_experience_years CHECK (experience_years IS NULL OR experience_years >= 0),
    CONSTRAINT chk_worker_hourly_rate CHECK (hourly_rate IS NULL OR hourly_rate > 0)
);

CREATE UNIQUE INDEX worker_email_lower_idx ON worker (LOWER(email)) WHERE email IS NOT NULL;

CREATE TABLE worker_skill
(
    worker_id UUID NOT NULL,
    skill_id  UUID NOT NULL,
    PRIMARY KEY (worker_id, skill_id),
    CONSTRAINT fk_worker_skill_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_worker_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
);
