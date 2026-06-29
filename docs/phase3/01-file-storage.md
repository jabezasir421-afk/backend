# Feature 8: File Storage Architecture

**Implement first** — portfolio, profile photos, and documents all depend on this module.

**Package:** `com.bluecollar.storage`  
**Migration:** V12

## Entity Relationships

```
UserAccount 1──N StoredFile
StoredFile ──(polymorphic)── WorkerPortfolioItem | WorkerCertificate | WorkerIdentityDocument | Customer | Worker
```

## Database Design

See [migrations.md](./migrations.md#v12--create_stored_file_table).

## Storage Abstraction

| Interface | Responsibility |
|-----------|----------------|
| `FileStorageService` | `store(key, InputStream, metadata)`, `delete(key)`, `getPresignedUrl(key, expiry)`, `exists(key)` |
| `LocalFileStorageService` | Default for dev/staging (`bluecollar.storage.type=local`) |
| `S3FileStorageService` | Production (`bluecollar.storage.type=s3`) — future swap via config |

## REST API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/files/upload` | Authenticated | Multipart upload; returns `StoredFileResponse` |
| `GET` | `/api/v1/files/{id}` | Owner or ADMIN | Metadata + presigned/download URL |
| `DELETE` | `/api/v1/files/{id}` | Owner or ADMIN | Soft-delete file + storage cleanup (async) |

## DTOs

```java
// Upload — multipart: file + fileCategory + optional entityType/entityId
record StoredFileResponse(
    UUID id, String fileCategory, String originalName, String mimeType,
    long sizeBytes, String downloadUrl, Instant createdAt
);

record FileUploadResponse(StoredFileResponse file);
```

**File categories:** `PROFILE_PHOTO`, `PORTFOLIO_IMAGE`, `CERTIFICATE`, `IDENTITY_DOC`

## Validation Rules

| Category | Allowed MIME | Max size |
|----------|--------------|----------|
| `PROFILE_PHOTO`, `PORTFOLIO_IMAGE` | `image/jpeg`, `image/png`, `image/webp` | 5 MB |
| `CERTIFICATE`, `IDENTITY_DOC` | above + `application/pdf` | 10 MB |

- DB constraint: max 10 MB (`size_bytes <= 10485760`)

## Business Rules

- Upload creates `stored_file` row first; storage write is atomic with DB transaction.
- Soft-delete sets `active = false`; background job purges storage after 7 days.
- Profile photo: one active per worker/customer; new upload replaces previous.
- Identity docs are **never** returned via public URLs — admin-only access.

## Security

- Validate `owner_user_id` matches `SecurityUtils.getCurrentUser().userAccountId()`.
- Scan MIME type from content (not just header); reject mismatches.
- Presigned URLs expire; no permanent public buckets for sensitive docs.
- Rate limit uploads: 30/hour per user.

## Exceptions

| Exception | HTTP Status |
|-----------|-------------|
| `FileNotFoundException` | 404 |
| `FileUploadException` | 400 |
