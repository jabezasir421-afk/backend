# Phase 3 Entity Relationships

## Full ER Diagram

```mermaid
erDiagram
    UserAccount ||--o| Customer : "1:1"
    UserAccount ||--o| Worker : "1:1 optional"
    UserAccount ||--o{ RefreshToken : "1:N"
    UserAccount ||--o{ StoredFile : "owns"
    UserAccount ||--o{ Notification : "receives"
    UserAccount ||--|| NotificationPreference : "has"
    UserAccount ||--o{ AuditLog : "performs"

    Customer ||--o{ Address : "1:N"
    Customer ||--o{ Booking : "1:N"
    Customer ||--o{ Review : "1:N"

    Worker }o--|| Category : "N:1"
    Worker }o--o{ Skill : "N:M"
    Worker ||--o{ Booking : "1:N"
    Worker ||--o{ Review : "1:N"
    Worker ||--o{ WorkerPortfolioItem : "has"
    Worker ||--o{ WorkerCertificate : "has"
    Worker ||--o{ WorkerIdentityDocument : "has"
    Worker ||--o{ WorkerWorkingHours : "has"
    Worker ||--o{ WorkerScheduleOverride : "has"
    Worker ||--o{ WorkerServiceArea : "serves"
    Worker ||--o| StoredFile : "profile_photo"

    WorkerPortfolioItem ||--|| StoredFile : "file"
    WorkerCertificate ||--|| StoredFile : "file"
    WorkerIdentityDocument ||--|| StoredFile : "file"

    Category ||--o{ Booking : "1:N"
    Address ||--o{ Booking : "1:N"
    Booking ||--|| Review : "1:1"

    Review ||--o{ ReviewReport : "reported"
    Notification ||--o| EmailOutbox : "email"

    AnalyticsDailySnapshot ||--o{ AnalyticsCategoryRanking : "contains"
    AnalyticsDailySnapshot ||--o{ AnalyticsWorkerRanking : "contains"
```

## New Entities Summary

| Entity | Table | Package |
|--------|-------|---------|
| `StoredFile` | `stored_file` | `storage` |
| `WorkerPortfolioItem` | `worker_portfolio_item` | `portfolio` |
| `WorkerCertificate` | `worker_certificate` | `portfolio` |
| `WorkerIdentityDocument` | `worker_identity_document` | `portfolio` |
| `WorkerServiceArea` | `worker_service_area` | `search` |
| `Notification` | `notification` | `notification` |
| `NotificationPreference` | `notification_preference` | `notification` |
| `EmailOutbox` | `email_outbox` | `notification` |
| `AnalyticsDailySnapshot` | `analytics_daily_snapshot` | `analytics` |
| `AnalyticsCategoryRanking` | `analytics_category_ranking` | `analytics` |
| `AnalyticsWorkerRanking` | `analytics_worker_ranking` | `analytics` |
| `WorkerWorkingHours` | `worker_working_hours` | `availability` |
| `WorkerScheduleOverride` | `worker_schedule_override` | `availability` |
| `ReviewReport` | `review_report` | `review` |
| `AuditLog` | `audit_log` | `audit` |

## Extended Existing Entities

### Worker (new columns)

| Column | Type | Purpose |
|--------|------|---------|
| `primary_city` | VARCHAR(100) | Search by city |
| `primary_state` | VARCHAR(100) | State filter |
| `profile_photo_file_id` | UUID FK | Profile photo |
| `profile_completion_percent` | SMALLINT | Completion % |
| `online_status` | VARCHAR(20) | ONLINE / OFFLINE |
| `last_seen_at` | TIMESTAMP | Heartbeat |
| `vacation_mode` | BOOLEAN | Vacation flag |
| `vacation_start` | DATE | Vacation range |
| `vacation_end` | DATE | Vacation range |

### Review (new columns)

| Column | Type | Purpose |
|--------|------|---------|
| `moderation_status` | VARCHAR(20) | PENDING, APPROVED, REJECTED, HIDDEN |
| `moderated_by` | UUID FK | Admin user |
| `moderated_at` | TIMESTAMP | Moderation time |
| `moderation_notes` | VARCHAR(500) | Admin notes |

## Phase 1–2 Entities (unchanged)

All extend `BaseEntity` with `UUID id`, `Instant createdAt`, `Instant updatedAt`.
