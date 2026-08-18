# Feature 5: Analytics

**Package:** `com.bluecollar.analytics`  
**Migration:** V17

## Capabilities

- Total workers
- Active customers
- Daily bookings
- Top categories
- Most hired workers
- Rating statistics

## Entity Relationships

```
AnalyticsDailySnapshot
AnalyticsCategoryRanking ── Category
AnalyticsWorkerRanking ── Worker
```

## Database Design

See [migrations.md](./migrations.md#v17--create_analytics_tables).

## REST API Endpoints

**Base:** `/api/v1/admin/analytics` — ADMIN only

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/analytics/overview` | Totals: workers, customers, bookings, avg rating |
| `GET` | `/api/v1/admin/analytics/bookings/daily` | Daily booking counts (date range) |
| `GET` | `/api/v1/admin/analytics/categories/top` | Top categories (limit, date range) |
| `GET` | `/api/v1/admin/analytics/workers/top` | Most hired workers (limit, date range) |
| `GET` | `/api/v1/admin/analytics/ratings` | Rating distribution (1–5 stars) |
| `GET` | `/api/v1/admin/analytics/growth` | Worker/customer growth over time |
| `POST` | `/api/v1/admin/analytics/refresh` | Manual snapshot refresh (async) |

## DTOs

```java
record AnalyticsOverviewResponse(
    long totalWorkers, long activeWorkers,
    long totalCustomers, long activeCustomers,
    long totalBookings, long bookingsToday,
    BigDecimal averagePlatformRating,
    BigDecimal totalRevenue
);

record DailyBookingStatsResponse(LocalDate date, int created, int completed, int cancelled);
record TopCategoryResponse(UUID categoryId, String categoryName, long bookingCount, int rank);
record TopWorkerResponse(UUID workerId, String fullName, long completedBookings, int rank);
record RatingDistributionResponse(Map<Short, Long> distribution, BigDecimal average, long totalReviews);
record GrowthStatsResponse(List<DailyCount> workerGrowth, List<DailyCount> customerGrowth);
record DailyCount(LocalDate date, long count);
```

## Business Rules

- Nightly job (`@Scheduled` 02:00 UTC) computes snapshots for previous day.
- `active_workers`: `active = true AND verified = true`.
- `active_customers`: `active = true` with booking in last 90 days OR registered in last 30 days.
- Top categories/workers: `COMPLETED` bookings in date range.
- Rating stats from `review` where `active = true`.
- `AdminDashboardService` can delegate to analytics for consistency.

## Security

- ADMIN only; no public analytics in Phase 3.
