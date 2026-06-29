# Feature 1: Worker Portfolio

**Package:** `com.bluecollar.portfolio`  
**Migration:** V13

## Capabilities

- Upload work images (portfolio)
- Experience certificates
- Identity verification documents
- Profile completion percentage

## Entity Relationships

```
Worker 1──N WorkerPortfolioItem ──1 StoredFile
Worker 1──N WorkerCertificate ──1 StoredFile
Worker 1──N WorkerIdentityDocument ──1 StoredFile
Worker ──1 StoredFile (profile_photo)
Worker columns: primary_city, primary_state, profile_completion_percent
```

## Database Design

See [migrations.md](./migrations.md#v13--create_worker_portfolio_tables).

## REST API Endpoints

**Base:** `/api/v1/workers/me/portfolio` — `@PreAuthorize("hasRole('WORKER')")`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/workers/me/portfolio` | Full portfolio summary + completion % |
| `GET` | `/api/v1/workers/me/portfolio/completion` | Profile completion breakdown |
| `POST` | `/api/v1/workers/me/portfolio/images` | Add portfolio image |
| `PUT` | `/api/v1/workers/me/portfolio/images/{id}` | Update title/description/order |
| `DELETE` | `/api/v1/workers/me/portfolio/images/{id}` | Soft-delete image |
| `POST` | `/api/v1/workers/me/portfolio/certificates` | Add certificate |
| `PUT` | `/api/v1/workers/me/portfolio/certificates/{id}` | Update certificate metadata |
| `DELETE` | `/api/v1/workers/me/portfolio/certificates/{id}` | Soft-delete |
| `POST` | `/api/v1/workers/me/portfolio/identity-documents` | Submit identity doc |
| `GET` | `/api/v1/workers/me/portfolio/identity-documents` | List own docs (status only) |
| `PUT` | `/api/v1/workers/me/profile-photo` | Set profile photo from uploaded fileId |
| `GET` | `/api/v1/workers/{id}/portfolio` | **Public** — portfolio images only |

## DTOs

```java
record AddPortfolioImageRequest(
    @NotNull UUID fileId,
    @Size(max = 200) String title,
    @Size(max = 1000) String description,
    @Min(0) @Max(99) Short displayOrder
);

record AddCertificateRequest(
    @NotNull UUID fileId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 200) String issuingOrg,
    LocalDate issueDate,
    LocalDate expiryDate
);

record AddIdentityDocumentRequest(
    @NotNull UUID fileId,
    @NotNull DocumentType documentType,
    @Size(max = 50) String documentNumber
);

record ProfileCompletionResponse(
    int completionPercent,
    Map<String, Boolean> sections,
    List<String> missingFields
);

record PortfolioSummaryResponse(
    UUID workerId, String profilePhotoUrl, int completionPercent,
    List<PortfolioImageResponse> images,
    List<CertificateResponse> certificates
);

enum DocumentType { AADHAAR, PAN, DRIVING_LICENSE, OTHER }
enum VerificationStatus { PENDING, VERIFIED, REJECTED }
```

## Validation Rules

- Max portfolio images per worker: **20**
- Max certificates: **10**
- Max identity docs: **3** (one per doc type)
- `displayOrder`: 0–99

## Profile Completion Calculation

| Section | Weight | Criteria |
|---------|--------|----------|
| Basic info | 15% | firstName, lastName, phone, email, primaryCity |
| Bio & rate | 10% | bio (min 50 chars), hourlyRate |
| Skills | 10% | ≥ 1 skill |
| Profile photo | 10% | profilePhotoFileId set |
| Portfolio | 20% | ≥ 3 portfolio images |
| Certificate | 15% | ≥ 1 certificate uploaded |
| Identity | 20% | ≥ 1 identity doc PENDING or VERIFIED |

Recalculate on every portfolio mutation (sync in service).

## Business Rules

- Only self-registered workers (`user_account_id` linked) manage `/me/portfolio`.
- Identity document numbers encrypted with AES-GCM; never logged.
- Certificate/identity `verification_status` changed only by admin.
- Worker `verified = true` requires identity doc VERIFIED + admin approval.
- Public portfolio: only `active = true` images, ordered by `display_order`.

## Security

- Identity docs and certificates: worker owner + ADMIN only.
- Public portfolio: images only; no identity storage keys exposed.
- Document numbers masked in responses (`XXXXXX1234`).

## Exceptions

| Exception | HTTP Status |
|-----------|-------------|
| `MaxPortfolioItemsExceededException` | 409 |
| `IdentityDocumentNotFoundException` | 404 |
