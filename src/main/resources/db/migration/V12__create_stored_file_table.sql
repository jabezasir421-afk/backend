CREATE TABLE stored_file
(
    id              UUID PRIMARY KEY,
    owner_user_id   UUID         NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       UUID,
    file_category   VARCHAR(30)  NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    checksum_sha256 VARCHAR(64),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_stored_file_owner FOREIGN KEY (owner_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_stored_file_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT chk_stored_file_category CHECK (file_category IN (
                                                                 'PROFILE_PHOTO', 'PORTFOLIO_IMAGE', 'CERTIFICATE',
                                                                 'IDENTITY_DOC'
        ))
);

CREATE UNIQUE INDEX stored_file_storage_key_idx ON stored_file (storage_key);
CREATE INDEX stored_file_entity_idx ON stored_file (entity_type, entity_id) WHERE active = TRUE;
CREATE INDEX stored_file_owner_idx ON stored_file (owner_user_id);
