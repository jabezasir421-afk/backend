# Phase 3 Domain Exceptions

Register all new exceptions in `GlobalExceptionHandler` following existing status conventions.

## Exception Catalog

| Exception | HTTP Status | Package | Trigger |
|-----------|-------------|---------|---------|
| `FileNotFoundException` | 404 NOT_FOUND | `storage.exception` | File ID not found or inactive |
| `FileUploadException` | 400 BAD_REQUEST | `storage.exception` | Invalid MIME, size, or storage failure |
| `MaxPortfolioItemsExceededException` | 409 CONFLICT | `portfolio.exception` | Portfolio/certificate/doc limit exceeded |
| `IdentityDocumentNotFoundException` | 404 NOT_FOUND | `portfolio.exception` | Identity doc not found |
| `PortfolioItemNotFoundException` | 404 NOT_FOUND | `portfolio.exception` | Portfolio item not found |
| `WorkerNotAvailableException` | 409 CONFLICT | `availability.exception` | Worker not available for booking date/slot |
| `InvalidScheduleException` | 400 BAD_REQUEST | `availability.exception` | Invalid working hours or override |
| `NotificationNotFoundException` | 404 NOT_FOUND | `notification.exception` | Notification not found |
| `ReviewReportAlreadyExistsException` | 409 CONFLICT | `review.exception` | Duplicate open report |
| `ReviewModerationException` | 422 UNPROCESSABLE_CONTENT | `review.exception` | Invalid moderation transition |

## Existing Patterns to Follow

```java
// Example: storage/exception/FileNotFoundException.java
public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(UUID id) {
        super("File not found with id: " + id);
    }
}
```

## GlobalExceptionHandler Additions

Map new exceptions alongside existing ones:

| Pattern | Status |
|---------|--------|
| `*NotFoundException` | 404 |
| `*AlreadyExistsException`, `MaxPortfolioItemsExceededException`, `WorkerNotAvailableException` | 409 |
| `ReviewModerationException` | 422 |
| `FileUploadException`, `InvalidScheduleException` | 400 |

## Package Structure

```
{module}/exception/
  ├── XxxNotFoundException.java
  ├── XxxAlreadyExistsException.java
  └── ...
```

No changes to `ErrorResponse` format — errors remain outside `ApiResponse` wrapper.
