# BlueCollar Backend

BlueCollar is a marketplace platform that connects skilled blue-collar workers with customers. This Spring Boot service provides worker and customer management, service discovery, bookings, reviews, notifications, analytics, and administration.

## Features

- Worker and customer profiles, skills, portfolios, and availability
- Service discovery with category, skill, city, and rating filters
- Booking lifecycle and scheduling
- Reviews, moderation, and ratings
- JWT authentication, refresh-token rotation, and role-based authorization
- Email and in-app notifications
- File storage for profile photos, portfolios, and identity documents
- Audit logging, analytics, rate limiting, and idempotency support

## Tech stack

- Java 21
- Spring Boot 4.1.0
- Spring MVC, Spring Data JPA, Spring Security, and WebSocket
- PostgreSQL 16 and Flyway
- SpringDoc OpenAPI / Swagger UI
- Maven, JUnit 5, Testcontainers, JaCoCo, Checkstyle, PMD, and SpotBugs
- Docker and Docker Compose

## Getting started

### Prerequisites

- Java 21+
- Docker and Docker Compose

The repository includes the Maven wrapper, so a separate Maven installation is not required.

### Run with Docker Compose

```bash
git clone https://github.com/jabezasir421-afk/backend.git
cd backend
cp .env.example .env
```

Edit `.env` and replace the example passwords and JWT secret with strong values before starting the stack.

```bash
docker compose up -d --build
```

The API is served at `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui/index.html` when enabled by the application configuration.

### Run locally

Start PostgreSQL first (for example, with Docker Compose):

```bash
docker compose up -d postgres
```

Then run the application:

```bash
./mvnw spring-boot:run
```

Configure the database connection and application secrets through the environment variables documented in `.env.example`.

## Project structure

The codebase follows a package-by-feature structure under `src/main/java/com/bluecollar/`, including:

```text
address/       admin/          analytics/      audit/
auth/          availability/   booking/        category/
common/        customer/       notification/   portfolio/
review/        search/         skill/           storage/
worker/
```

## Documentation

- Production readiness report: `docs/PRODUCTION_READINESS_REPORT.md`
- Architecture and implementation notes: `docs/phase3/` and `docs/phase4/`
- Database migrations: `src/main/resources/db/migration/`
- Backup and restore runbook: `docs/database/BACKUP_AND_RESTORE_RUNBOOK.md`
- Rollback and recovery: `docs/database/ROLLBACK_RECOVERY.md`

## Testing and quality checks

Run the test suite:

```bash
./mvnw test
```

Run the complete verification lifecycle:

```bash
./mvnw clean verify
```

This runs the configured build, tests, and quality checks. The CI workflow also runs PostgreSQL-backed integration checks and builds a Docker image as a smoke test.
