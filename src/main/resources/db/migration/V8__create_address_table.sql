CREATE TABLE address
(
    id           UUID PRIMARY KEY,
    customer_id  UUID         NOT NULL,
    label        VARCHAR(50)  NOT NULL,
    address_type VARCHAR(30)  NOT NULL,
    line1        VARCHAR(255) NOT NULL,
    line2        VARCHAR(255),
    landmark     VARCHAR(255),
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    pincode      VARCHAR(6)   NOT NULL,
    latitude     NUMERIC(10, 7),
    longitude    NUMERIC(10, 7),
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_address_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE
);

CREATE INDEX address_customer_idx ON address (customer_id);

CREATE UNIQUE INDEX address_customer_default_idx ON address (customer_id) WHERE is_default = TRUE AND active = TRUE;
