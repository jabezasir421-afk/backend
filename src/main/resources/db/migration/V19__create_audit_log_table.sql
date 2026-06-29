CREATE TABLE audit_log
(
    id             UUID PRIMARY KEY,
    actor_user_id  UUID,
    actor_role     VARCHAR(30),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(50)  NOT NULL,
    entity_id      UUID,
    old_value      JSONB,
    new_value      JSONB,
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX audit_log_created_idx ON audit_log (created_at DESC);
CREATE INDEX audit_log_correlation_idx ON audit_log (correlation_id);
