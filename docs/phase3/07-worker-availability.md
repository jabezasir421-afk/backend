# Feature 6: Worker Availability

**Package:** `com.bluecollar.availability`  
**Migration:** V18

## Capabilities

- Online/Offline status
- Working hours
- Weekly schedule
- Vacation mode

## Semantic Distinction

| Field / Table | Meaning |
|---------------|---------|
| `worker.available` (existing) | Worker accepts bookings globally |
| `worker.online_status` | Real-time presence (heartbeat-driven) |
| `worker.vacation_mode` | Blocks all bookings in date range |
| `worker_working_hours` | Default weekly template |
| `worker_schedule_override` | Per-date exceptions |

## Entity Relationships

```
Worker 1──N WorkerWorkingHours
Worker 1──N WorkerScheduleOverride
```

## Database Design

See [migrations.md](./migrations.md#v18--create_worker_availability_tables).

## REST API Endpoints

### Worker Self-Service — `/api/v1/workers/me/availability`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/workers/me/availability` | Full availability config |
| `PUT` | `/api/v1/workers/me/availability/status` | Set online/offline |
| `PUT` | `/api/v1/workers/me/availability/bookable` | Toggle `available` flag |
| `PUT` | `/api/v1/workers/me/availability/working-hours` | Replace weekly schedule |
| `GET` | `/api/v1/workers/me/availability/working-hours` | Get weekly schedule |
| `PUT` | `/api/v1/workers/me/availability/vacation` | Enable/disable vacation mode |
| `POST` | `/api/v1/workers/me/availability/overrides` | Add date override |
| `DELETE` | `/api/v1/workers/me/availability/overrides/{id}` | Remove override |
| `POST` | `/api/v1/workers/me/availability/heartbeat` | Update `last_seen_at` |

### Public — `/api/v1/workers/{id}/availability`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/workers/{id}/availability` | Public summary |
| `GET` | `/api/v1/workers/{id}/availability/slots` | Slots for `?date=YYYY-MM-DD` |

## DTOs

```java
record UpdateOnlineStatusRequest(@NotNull OnlineStatus status);
enum OnlineStatus { ONLINE, OFFLINE }

record WorkingHoursEntry(
    @Min(1) @Max(7) short dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
);

record UpdateWorkingHoursRequest(
    @NotEmpty @Size(max = 7) List<WorkingHoursEntry> schedule
);

record VacationModeRequest(
    @NotNull Boolean enabled,
    @FutureOrPresent LocalDate vacationStart,
    LocalDate vacationEnd
);

record ScheduleOverrideRequest(
    @NotNull @FutureOrPresent LocalDate overrideDate,
    @NotNull Boolean available,
    LocalTime startTime,
    LocalTime endTime,
    @Size(max = 200) String reason
);

record AvailabilitySummaryResponse(
    boolean bookable, OnlineStatus onlineStatus,
    boolean vacationMode, LocalDate vacationStart, LocalDate vacationEnd,
    List<WorkingHoursEntry> workingHours
);

record AvailableSlotResponse(LocalTime startTime, LocalTime endTime);
```

## Business Rules

- Heartbeat every 5 min; stale `last_seen_at > 15 min` → auto `OFFLINE`.
- Vacation: `vacation_start <= date <= vacation_end` → excluded from search.
- Slot calculation: working hours − bookings − overrides.
- Booking creation validates availability for `scheduledDate` + `timeSlot`.
- Max overrides: 90 days ahead, max 30 records.

## Security

- Self endpoints: WORKER role + ownership.
- Public endpoints: no vacation/override reasons.

## Exceptions

| Exception | HTTP Status |
|-----------|-------------|
| `WorkerNotAvailableException` | 409 |
| `InvalidScheduleException` | 400 |
