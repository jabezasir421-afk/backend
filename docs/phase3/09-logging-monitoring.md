# Feature 9: Logging & Monitoring

**Packages:** `com.bluecollar.audit`, `com.bluecollar.common.filter`  
**Migration:** V20

## Capabilities

- Structured logging
- Audit logging
- Request tracing
- Health endpoints

## Database Design

See [migrations.md](./migrations.md#v20--create_audit_log_table).

## Application Components

| Component | Package | Purpose |
|-----------|---------|---------|
| `CorrelationIdFilter` | `common/filter` | Read/generate `X-Correlation-Id`; MDC |
| `AuditLogService` | `audit` | `@Async` persist audit entries |
| Explicit audit calls / aspect | `audit` | Admin mutations, auth, booking transitions |
| `logback-spring.xml` | `resources` | JSON structured logging (Logstash encoder) |
| Actuator | config | Health, info, metrics |

## REST API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/admin/audit-logs` | ADMIN | Query audit logs |
| `GET` | `/actuator/health` | Public | Health check (DB, disk, mail) |
| `GET` | `/actuator/info` | Public | App version, build info |
| `GET` | `/actuator/metrics` | ADMIN | Micrometer metrics |

### Audit Log Query Params

`entityType`, `entityId`, `actorUserId`, `from`, `to`, `page`, `size`

## DTOs

```java
record AuditLogResponse(
    UUID id, UUID actorUserId, String actorRole, String action,
    String entityType, UUID entityId,
    Instant createdAt, String correlationId
);
```

## Structured Logging Fields

Every log line: `correlationId`, `userId`, `requestPath`, `durationMs`, `statusCode`.

## Business Rules

- Audit: all admin actions, booking state changes, login/logout, verification decisions.
- `old_value`/`new_value`: JSON diff of changed fields only.
- Retention: 2 years; archive job for older records.
- Health check: DB ping, storage writable, email outbox backlog < 1000.

## Security

- Actuator: expose `health`, `info` publicly; `metrics`, `env` ADMIN or disabled in prod.
- Audit logs: ADMIN only; no password/hash in JSON values.
- Correlation ID returned in header `X-Correlation-Id`.
