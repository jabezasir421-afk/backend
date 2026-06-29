# Feature 10: Performance Improvements

Cross-cutting — apply after core Phase 3 features stabilize (Sprint 3.9).

## Database Indexing

| Table | Index | Purpose |
|-------|-------|---------|
| `worker` | `(category_id, active, verified, average_rating)` partial | Search |
| `worker` | `LOWER(primary_city)` partial | City search |
| `worker_service_area` | `(LOWER(city), LOWER(state))` partial | Multi-city search |
| `booking` | `(worker_id, scheduled_date, status)` | Availability slots |
| `booking` | `(status, created_at)` | Analytics |
| `review` | `(worker_id, moderation_status, active)` | Rating calc |
| `notification` | `(recipient_user_id, created_at DESC)` | Feed |
| `email_outbox` | `(status, created_at)` partial PENDING | Outbox poller |
| `stored_file` | `(entity_type, entity_id)` partial | File lookups |
| `audit_log` | `(entity_type, entity_id, created_at DESC)` | Audit queries |

Most indexes defined in [migrations.md](./migrations.md).

## Caching Strategy

**Dependencies:** `spring-boot-starter-cache` + Caffeine (dev) / Redis (prod).

| Cache Name | Key | TTL | Content |
|------------|-----|-----|---------|
| `categories` | `all` | 1 hour | Active categories list |
| `skills` | `all` | 1 hour | Active skills list |
| `worker-search` | filter hash | 5 min | Search result pages |
| `worker-profile` | workerId | 10 min | Public worker detail |
| `analytics-overview` | `overview` | 15 min | Dashboard totals |
| `review-stats` | workerId | 10 min | Worker review statistics |

**Eviction:** `@CacheEvict` on category/skill CRUD, worker update, review moderation, verification.

## Query Optimization Rules

1. List endpoints: projection queries — no lazy-load of collections.
2. Search: single query with JOIN FETCH skills or batch `@EntityGraph`.
3. Analytics: read from `analytics_daily_snapshot` — avoid full table scans.
4. Pagination: always indexed sort column (`created_at`, `average_rating`).
5. `open-in-view: false` (already set) — enforce in new modules.

## API Response Optimization

- Public worker cards: flat DTOs, max 1 level nesting.
- Optional `fields` query param for admin lists (Phase 3.1).
- Enable gzip: `server.compression.enabled=true`.
- Page size cap: 50 everywhere.

## Connection Pool (optional tuning)

```yaml
spring.datasource.hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
```
