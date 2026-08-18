# Feature 3: Notifications

**Package:** `com.bluecollar.notification`  
**Migration:** V15

## Capabilities

- Email notifications
- Booking updates
- Account notifications
- Review notifications

## Entity Relationships

```
UserAccount 1──N Notification
UserAccount 1──1 NotificationPreference
Notification 1──0..1 EmailOutbox
```

## Database Design

See [migrations.md](./migrations.md#v15--create_notification_tables).

## REST API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/notifications` | Authenticated | Paginated in-app notifications |
| `GET` | `/api/v1/notifications/unread-count` | Authenticated | Badge count |
| `PUT` | `/api/v1/notifications/{id}/read` | Owner | Mark single read |
| `PUT` | `/api/v1/notifications/read-all` | Owner | Mark all read |
| `GET` | `/api/v1/notifications/preferences` | Authenticated | Get preferences |
| `PUT` | `/api/v1/notifications/preferences` | Authenticated | Update preferences |

## DTOs

```java
record NotificationResponse(
    UUID id, String type, String title, String body,
    String referenceType, UUID referenceId,
    Instant readAt, Instant createdAt
);

record UpdateNotificationPreferenceRequest(
    Boolean emailBookingUpdates,
    Boolean emailAccountUpdates,
    Boolean emailReviewUpdates,
    Boolean inAppEnabled
);

record NotificationPreferenceResponse(
    boolean emailBookingUpdates, boolean emailAccountUpdates,
    boolean emailReviewUpdates, boolean inAppEnabled
);
```

### Notification Types

`BOOKING_CREATED`, `BOOKING_ACCEPTED`, `BOOKING_REJECTED`, `BOOKING_STARTED`, `BOOKING_COMPLETED`, `BOOKING_CANCELLED`, `REVIEW_RECEIVED`, `REVIEW_MODERATED`, `ACCOUNT_WELCOME`, `ACCOUNT_PASSWORD_CHANGED`, `ACCOUNT_DEACTIVATED`, `VERIFICATION_APPROVED`, `VERIFICATION_REJECTED`, `IDENTITY_DOC_VERIFIED`, `IDENTITY_DOC_REJECTED`

## Event Triggers

| Event | Recipients | Type |
|-------|------------|------|
| Booking created | Worker | `BOOKING_CREATED` |
| Booking accepted/rejected/cancelled/completed | Customer + Worker | respective `BOOKING_*` |
| Review created | Worker | `REVIEW_RECEIVED` |
| Identity doc verified/rejected | Worker | `IDENTITY_DOC_*` |
| Worker verified/unverified | Worker | `VERIFICATION_*` |
| Account deactivated | User | `ACCOUNT_DEACTIVATED` |

## Business Rules

- `@TransactionalEventListener(phase = AFTER_COMMIT)` from domain services.
- Email: write to `email_outbox`; `@Scheduled` poller (30s) via `JavaMailSender`.
- Respect `notification_preference`; skip email → `SKIPPED`.
- Idempotency: same event + referenceId within 1 minute → no duplicate.

## Security

- Users read only their own notifications.
- Email bodies contain no sensitive tokens.
- Admin cannot read user notifications.

## Exceptions

| Exception | HTTP Status |
|-----------|-------------|
| `NotificationNotFoundException` | 404 |
