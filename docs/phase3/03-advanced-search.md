# Feature 2: Advanced Search

**Package:** `com.bluecollar.search`  
**Migration:** V14

## Capabilities

Search workers by city, category, skill, rating, availability, experience, price range — with pagination and sorting.

## Entity Relationships

```
Worker 1──N WorkerServiceArea
Worker N──M Skill (worker_skill)
Worker N──1 Category
WorkerAvailability feeds availability filter
```

## Database Design

See [migrations.md](./migrations.md#v14--create_search_support_indexes).

## REST API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/search/workers` | Public | Advanced search |
| `GET` | `/api/v1/search/workers/suggestions` | Public | Autocomplete for city/category |
| `GET` | `/api/v1/admin/search/workers` | ADMIN | Includes unverified/inactive |

### Query Parameters

| Param | Type | Description |
|-------|------|-------------|
| `city` | string | Match `primary_city` or `worker_service_area.city` |
| `state` | string | Optional state filter |
| `categoryId` | UUID | Filter by category |
| `skillIds` | UUID[] | Workers having ALL specified skills |
| `minRating` | decimal | `average_rating >= minRating` |
| `available` | boolean | `worker.available` + availability checks |
| `availableOn` | date | Available on specific date |
| `minExperience` | int | `experience_years >=` |
| `maxExperience` | int | `experience_years <=` |
| `minPrice` | decimal | `hourly_rate >=` |
| `maxPrice` | decimal | `hourly_rate <=` |
| `verified` | boolean | Default `true` for public |
| `matchAnySkill` | boolean | OR logic for skills (default AND) |
| `sort` | string | `rating`, `price`, `experience`, `createdAt` |
| `direction` | string | `asc` / `desc` |
| `page`, `size` | int | Default size 20, max 50 |

## DTOs

```java
record WorkerSearchResultResponse(
    UUID id, String fullName, String primaryCity, String categoryName,
    BigDecimal hourlyRate, BigDecimal averageRating, int reviewCount,
    boolean available, boolean verified, String profilePhotoUrl,
    List<String> skillNames, int profileCompletionPercent
);

record WorkerSearchResponse(
    List<WorkerSearchResultResponse> results,
    long totalElements, int page, int size,
    SearchFiltersApplied filters
);

record SearchFiltersApplied(
    String city, UUID categoryId, List<UUID> skillIds,
    BigDecimal minRating, Boolean available, LocalDate availableOn,
    Integer minExperience, Integer maxExperience,
    BigDecimal minPrice, BigDecimal maxPrice
);
```

## Validation Rules

- `minRating`: 0–5
- `minPrice`/`maxPrice`: positive; `minPrice <= maxPrice`
- `minExperience`/`maxExperience`: 0–50; min ≤ max
- `availableOn`: `@FutureOrPresent` when provided
- `size`: max 50

## Business Rules

- Public search: `active = true`, `verified = true` by default.
- `availableOn` intersects vacation mode, weekly schedule, and existing bookings.
- Skill filter: AND by default; `matchAnySkill=true` for OR.
- Denormalized `average_rating` for sort/filter.
- Cache results keyed by filter hash for 5 minutes.

## Query Strategy

- JPA `Specification<Worker>` or native query with dynamic WHERE.
- Avoid N+1: batch-fetch skills and profile photo URLs.
- Project to DTO in repository for list endpoints.

## Security

- Public endpoint — no PII beyond name, city, ratings.
- Admin search with `includeInactive=true`.
