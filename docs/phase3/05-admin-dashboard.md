# Feature 4: Admin Dashboard (Extended)

**Package:** `com.bluecollar.admin` (extend existing)  
**Migration:** Uses `audit_log` (V20)

## Capabilities

- User management
- Worker verification (identity docs, certificates)
- Booking monitoring
- Category & Skill management stats
- Reports and analytics exports

## REST API Endpoints

### User Management — `/api/v1/admin/users`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/users` | List users (filter: role, active, email) |
| `GET` | `/api/v1/admin/users/{id}` | User detail with linked profile |
| `PUT` | `/api/v1/admin/users/{id}/activate` | Reactivate account |
| `PUT` | `/api/v1/admin/users/{id}/deactivate` | Deactivate |
| `PUT` | `/api/v1/admin/users/{id}/role` | Change role (restricted) |

### Worker Verification — `/api/v1/admin/workers`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/workers/pending-verification` | Pending identity docs |
| `GET` | `/api/v1/admin/workers/{id}/identity-documents` | Full doc list + download URLs |
| `PUT` | `/api/v1/admin/workers/{id}/identity-documents/{docId}/verify` | Approve identity doc |
| `PUT` | `/api/v1/admin/workers/{id}/identity-documents/{docId}/reject` | Reject with reason |
| `PUT` | `/api/v1/admin/workers/{id}/certificates/{certId}/verify` | Verify certificate |
| `PUT` | `/api/v1/admin/workers/{id}/verify` | Final worker verification |
| `PUT` | `/api/v1/admin/workers/{id}/unverify` | Revoke verification |

### Booking Monitoring — `/api/v1/admin/bookings`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/bookings` | Filter: status, date range, customerId, workerId, city |
| `GET` | `/api/v1/admin/bookings/{id}/timeline` | Status transitions from audit log |
| `GET` | `/api/v1/admin/bookings/stats` | Counts by status for date range |

### Category & Skill Stats

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/categories/stats` | Booking count per category |
| `GET` | `/api/v1/admin/skills/stats` | Worker count per skill |

### Reports — `/api/v1/admin/reports`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/reports/bookings/export` | CSV export (date range) |
| `GET` | `/api/v1/admin/reports/workers/export` | CSV export |
| `GET` | `/api/v1/admin/reports/revenue` | Aggregated quoted/final amounts |

### Existing (Phase 2)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/admin/dashboard` | Dashboard stats |
| `PUT` | `/api/v1/admin/workers/{id}/deactivate` | Deactivate worker |

## DTOs

```java
record AdminUserResponse(
    UUID id, String email, String phoneNumber, UserRole role,
    boolean active, boolean emailVerified, Instant lastLoginAt,
    UUID profileId, String profileType
);

record VerifyIdentityDocumentRequest(@Size(max = 500) String notes);
record RejectIdentityDocumentRequest(@NotBlank @Size(max = 500) String rejectionReason);

record BookingTimelineEntry(
    Instant timestamp, String action, UUID actorUserId,
    String actorRole, String details
);

record AdminBookingStatsResponse(
    long pending, long accepted, long inProgress, long completed,
    long cancelled, long rejected, BigDecimal totalRevenue
);
```

## Business Rules

- Worker `verified = true` requires at least one `VERIFIED` identity document.
- Deactivating worker does not deactivate `UserAccount` (existing behavior).
- Deactivating customer deactivates `UserAccount` (existing behavior).
- Cannot demote last ADMIN; cannot change own role.
- All admin mutations write `audit_log` entry.

## Security

- All endpoints: `@PreAuthorize("hasRole('ADMIN')")`.
- Identity doc download URLs: admin-only, short-lived presigned.
- Export endpoints rate-limited; max 10,000 rows.
- CSV exports audit-logged.
