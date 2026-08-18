CREATE TABLE user_account
(
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    phone_number   VARCHAR(15)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(30)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX user_account_email_lower_idx ON user_account (LOWER(email));
