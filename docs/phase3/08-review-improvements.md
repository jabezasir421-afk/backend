# Feature 7: Review Improvements

**Package:** `com.bluecollar.review` (extend existing)  
**Migration:** V19

## Capabilities

- Review moderation
- Report inappropriate reviews
- Average rating calculation (revised)
- Review statistics

## Entity Relationships

```
Review ── moderation fields (moderation_status, moderated_by, etc.)
Review 1──N ReviewReport
UserAccount (admin) moderates Review
```

## Database Design

See [migrations.md](./migrations.md#v19--extend_review_moderation).

## REST API Endpoints

### Public / Customer

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/workers/{id}/reviews` | Public | Only `APPROVED` + `active` |
| `GET` | `/api/v1/workers/{id}/reviews/stats` | Public | Rating breakdown |
| `POST` | `/api/v1/reviews/{id}/report` | CUSTOMER/WORKER | Report review |

### Admin — `/api/v1/admin/reviews`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/reviews` | List with moderation filter |
| `GET` | `/api/v1/admin/reviews/pending` | Pending moderation queue |
| `PUT` | `/api/v1/admin/reviews/{id}/approve` | Approve review |
| `PUT` | `/api/v1/admin/reviews/{id}/reject` | Reject/hide review |
| `GET` | `/api/v1/admin/reviews/reports` | Open reports queue |
| `PUT` | `/api/v1/admin/reviews/reports/{id}/resolve` | Resolve report |

### Existing (Phase 2)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/reviews` | Create review |
| `GET` | `/api/v1/admin/reviews` | Admin list |
| `PUT` | `/api/v1/admin/reviews/{id}/deactivate` | Deactivate review |

## DTOs

```java
record ReportReviewRequest(
    @NotNull ReportReason reason,
    @Size(max = 1000) String description
);
enum ReportReason { INAPPROPRIATE, SPAM, FAKE, OFFENSIVE, OTHER }

record ReviewStatsResponse(
    BigDecimal averageRating, long totalReviews,
    Map<Short, Long> ratingDistribution,
    long fiveStarPercent, long fourStarPercent, ...
);

record AdminReviewResponse(
    UUID id, UUID workerId, UUID customerId,
    short rating, String comment,
    ModerationStatus moderationStatus, Instant moderatedAt,
    long reportCount, boolean active
);

record ModerateReviewRequest(@Size(max = 500) String notes);
enum ModerationStatus { PENDING, APPROVED, REJECTED, HIDDEN }
```

## Average Rating Calculation

```
average_rating = SUM(rating) / COUNT(*)
WHERE active = TRUE AND moderation_status = 'APPROVED'
```

Recalculate on: review create, deactivate, moderation status change.

## Business Rules

- New reviews: `moderation_status = APPROVED` by default.
- Flag as `PENDING` if comment contains blocked words.
- Hidden/rejected reviews excluded from public list and rating calc.
- Auto-hide when ≥ 3 OPEN reports → `moderation_status = PENDING`.
- One open report per user per review.

## Security

- Reporters cannot report own reviews.
- Admin moderation actions audit-logged.

## Exceptions

| Exception | HTTP Status |
|-----------|-------------|
| `ReviewReportAlreadyExistsException` | 409 |
| `ReviewModerationException` | 422 |
