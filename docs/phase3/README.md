# BlueCollar Phase 3 — Backend Architecture

Production-ready design for Phase 3 of the BlueCollar marketplace platform. This documentation extends the existing single-module Spring Boot application without implementation code — specifications for database, APIs, DTOs, business rules, and security.

## Current Stack (Phase 1–2)

- Spring Boot 4.x, Java 25, PostgreSQL, Flyway, JWT Authentication
- Modules: Category, Skill, Worker, Customer, Address, Booking, Review, Admin, Auth
- Patterns: `com.bluecollar.*` package-by-feature, `BaseEntity`, `ApiResponse`, record DTOs, Flyway migrations (V2–V11)

## Phase 3 Module Map

| Package | Path | Responsibility |
|---------|------|----------------|
| `storage` | `src/main/java/com/bluecollar/storage/` | File metadata, storage abstraction, uploads |
| `portfolio` | `src/main/java/com/bluecollar/portfolio/` | Portfolio, certificates, identity docs, profile completion |
| `availability` | `src/main/java/com/bluecollar/availability/` | Online status, working hours, schedule, vacation |
| `search` | `src/main/java/com/bluecollar/search/` | Advanced worker search |
| `notification` | `src/main/java/com/bluecollar/notification/` | In-app + email notifications |
| `review` (extend) | `src/main/java/com/bluecollar/review/` | Moderation, reports, statistics |
| `admin` (extend) | `src/main/java/com/bluecollar/admin/` | User mgmt, verification, monitoring |
| `analytics` | `src/main/java/com/bluecollar/analytics/` | Metrics, trends, leaderboards |
| `audit` | `src/main/java/com/bluecollar/audit/` | Audit log persistence |
| `common` (extend) | `src/main/java/com/bluecollar/common/` | Tracing filter, caching config |

Migrations: `src/main/resources/db/migration/` (V12 onward)

## Documentation Index

| Doc | Feature |
|-----|---------|
| [01-file-storage.md](./01-file-storage.md) | File Storage Architecture (implement first) |
| [02-worker-portfolio.md](./02-worker-portfolio.md) | Worker Portfolio |
| [03-advanced-search.md](./03-advanced-search.md) | Advanced Search |
| [04-notifications.md](./04-notifications.md) | Notifications |
| [05-admin-dashboard.md](./05-admin-dashboard.md) | Admin Dashboard (extended) |
| [06-analytics.md](./06-analytics.md) | Analytics |
| [07-worker-availability.md](./07-worker-availability.md) | Worker Availability |
| [08-review-improvements.md](./08-review-improvements.md) | Review Improvements |
| [09-logging-monitoring.md](./09-logging-monitoring.md) | Logging & Monitoring |
| [10-performance.md](./10-performance.md) | Performance Improvements |
| [entity-relationships.md](./entity-relationships.md) | Full ER diagram |
| [migrations.md](./migrations.md) | Consolidated Flyway SQL (V12–V20) |
| [configuration.md](./configuration.md) | New config properties & dependencies |
| [exceptions.md](./exceptions.md) | New domain exceptions |

## Recommended Implementation Order

| Phase | Sprint | Modules | Migrations | Rationale |
|-------|--------|---------|------------|-----------|
| 3.1 | 1 | `storage`, `audit` | V12, V20 | File features depend on storage; audit from day one |
| 3.2 | 2 | `portfolio` | V13 | Portfolio, profile completion, identity docs |
| 3.3 | 3 | `availability` | V18 | Real availability before search uses it |
| 3.4 | 4 | `search` | V14 | Depends on city, availability, ratings |
| 3.5 | 5 | `review` (extend) | V19 | Moderation before notifications reference reviews |
| 3.6 | 6 | `notification` | V15 | Event listeners need stable domain events |
| 3.7 | 7 | `admin` (extend) | — | Verification UI, user mgmt, monitoring |
| 3.8 | 8 | `analytics` | V17 | Snapshots need stable booking/review data |
| 3.9 | 9 | Performance | V14 indexes | Caching, query tuning after features stabilize |
| 3.10 | 10 | Logging & Monitoring | — | Actuator, structured logging polish |

## Integration with Existing Modules

| Existing Module | Phase 3 Integration |
|-----------------|---------------------|
| `WorkerService` | Profile completion, availability, portfolio summary |
| `BookingService` | Validate availability; emit notification events |
| `ReviewService` | Moderation filter on rating recalc; report flow |
| `AdminWorkerService` | Identity verification workflow |
| `AdminDashboardService` | Delegate to `AnalyticsService` |
| `SecurityConfig` | Permit public: search, portfolio images, review stats, health |
| `Customer` | Profile photo via same `storage` module |

## Layer Pattern (per domain)

```
controller → service (interface + *Impl) → repository → entity
                ↓
           dto (records) + mapper (@Component)
                ↓
           exception (domain-specific RuntimeException)
```
