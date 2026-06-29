CREATE TABLE customer
(
    id              UUID PRIMARY KEY,
    user_account_id UUID         NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    avatar_url      VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_customer_user_account FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);
