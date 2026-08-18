# Phase 3 Configuration & Dependencies

## New `pom.xml` Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-cache` | Caching abstraction |
| `caffeine` | Local cache (dev) |
| `spring-boot-starter-data-redis` | Redis cache (prod, optional) |
| `spring-boot-starter-actuator` | Health, metrics |
| `logstash-logback-encoder` | Structured JSON logs |
| `aws-sdk-s3` (optional) | S3 storage provider |

Already present but to be activated:
- `spring-boot-starter-mail` — email notifications (Phase 3.6)

## New Configuration Properties

Add to `src/main/resources/application.yml` under `bluecollar:`:

```yaml
bluecollar:
  storage:
    type: local
    local:
      base-path: ${STORAGE_PATH:/var/bluecollar/files}
    s3:
      bucket: ${S3_BUCKET:}
      region: ${S3_REGION:}
    presigned-url-expiry-seconds: 3600

  notification:
    email:
      enabled: true
      from: ${MAIL_FROM:noreply@bluecollar.com}
    outbox:
      poll-interval-ms: 30000
      max-retries: 3

  availability:
    heartbeat-timeout-minutes: 15

  analytics:
    snapshot-cron: "0 0 2 * * *"

  cache:
    type: caffeine

  encryption:
    document-key: ${DOCUMENT_ENCRYPTION_KEY}
```

## Typed Configuration Classes

| Class | Prefix | Package |
|-------|--------|---------|
| `StorageProperties` | `bluecollar.storage` | `storage.config` |
| `NotificationProperties` | `bluecollar.notification` | `notification.config` |
| `AvailabilityProperties` | `bluecollar.availability` | `availability.config` |
| `AnalyticsProperties` | `bluecollar.analytics` | `analytics.config` |
| `EncryptionProperties` | `bluecollar.encryption` | `common.config` |
| `CacheProperties` | `bluecollar.cache` | `common.config` |

Follow existing pattern: `@ConfigurationProperties` + `@EnableConfigurationProperties` (see `JwtProperties`, `AdminBootstrapProperties`).

## Mail Configuration (Spring)

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

## Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized
```

## SecurityConfig Updates

Add permit-all for new public endpoints:

- `GET /api/v1/search/workers`
- `GET /api/v1/search/workers/suggestions`
- `GET /api/v1/workers/{id}/portfolio`
- `GET /api/v1/workers/{id}/availability`
- `GET /api/v1/workers/{id}/availability/slots`
- `GET /api/v1/workers/{id}/reviews/stats`
- `GET /actuator/health`
- `GET /actuator/info`

## Environment Variables Summary

| Variable | Required | Description |
|----------|----------|-------------|
| `STORAGE_PATH` | Dev | Local file storage path |
| `S3_BUCKET`, `S3_REGION` | Prod (S3) | S3 configuration |
| `DOCUMENT_ENCRYPTION_KEY` | Yes | AES-256 key for identity doc numbers |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Email | SMTP settings |
| `MAIL_FROM` | Email | Sender address |
| `JWT_SECRET` | Yes | Existing — no change |

## Logback

Create `src/main/resources/logback-spring.xml` with Logstash JSON encoder. Include MDC fields: `correlationId`, `userId`, `requestPath`.
