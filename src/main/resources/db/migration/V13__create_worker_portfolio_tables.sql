ALTER TABLE worker
    ADD COLUMN primary_city VARCHAR(100);
ALTER TABLE worker
    ADD COLUMN primary_state VARCHAR(100);
ALTER TABLE worker
    ADD COLUMN profile_photo_file_id UUID;
ALTER TABLE worker
    ADD COLUMN profile_completion_percent SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE worker
    ADD CONSTRAINT fk_worker_profile_photo
        FOREIGN KEY (profile_photo_file_id) REFERENCES stored_file (id);
ALTER TABLE worker
    ADD CONSTRAINT chk_worker_profile_completion
        CHECK (profile_completion_percent >= 0 AND profile_completion_percent <= 100);

CREATE TABLE worker_portfolio_item
(
    id            UUID PRIMARY KEY,
    worker_id     UUID      NOT NULL,
    file_id       UUID      NOT NULL,
    title         VARCHAR(200),
    description   VARCHAR(1000),
    display_order SMALLINT  NOT NULL DEFAULT 0,
    active        BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_portfolio_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT uq_portfolio_file UNIQUE (file_id)
);

CREATE TABLE worker_certificate
(
    id                  UUID PRIMARY KEY,
    worker_id           UUID         NOT NULL,
    file_id             UUID         NOT NULL,
    title               VARCHAR(200) NOT NULL,
    issuing_org         VARCHAR(200),
    issue_date          DATE,
    expiry_date         DATE,
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT fk_certificate_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_certificate_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT chk_certificate_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE worker_identity_document
(
    id                  UUID PRIMARY KEY,
    worker_id           UUID        NOT NULL,
    file_id             UUID        NOT NULL,
    document_type       VARCHAR(30) NOT NULL,
    document_number     VARCHAR(50),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_by         UUID,
    verified_at         TIMESTAMP,
    rejection_reason    VARCHAR(500),
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    CONSTRAINT fk_identity_worker FOREIGN KEY (worker_id) REFERENCES worker (id) ON DELETE CASCADE,
    CONSTRAINT fk_identity_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT fk_identity_verified_by FOREIGN KEY (verified_by) REFERENCES user_account (id),
    CONSTRAINT chk_identity_type CHECK (document_type IN ('AADHAAR', 'PAN', 'DRIVING_LICENSE', 'OTHER')),
    CONSTRAINT chk_identity_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX worker_primary_city_idx ON worker (LOWER(primary_city)) WHERE active = TRUE;
CREATE INDEX portfolio_worker_idx ON worker_portfolio_item (worker_id) WHERE active = TRUE;
