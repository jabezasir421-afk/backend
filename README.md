# BlueCollar Backend

BlueCollar is a marketplace platform that connects skilled blue-collar workers with customers. This backend service provides the API layer for worker and customer management, service discovery, bookings, reviews, notifications, analytics, and administrative operations.

## Features

- Worker and customer profiles
- Category and skill management
- Address and availability tracking
- Booking and service lifecycle flows
- Review, moderation, and rating logic
- Notification and audit capabilities
- Admin dashboards and verification workflows
- JWT-based authentication and authorization
- File storage abstraction for profile and document uploads
- Search and analytics support

## Tech Stack

- Java 21
- Spring Boot 4.1.0
- Spring Data JPA
- Spring Security + JWT
- PostgreSQL 16
- Flyway database migrations
- Spring Web MVC and WebSocket
- SpringDoc OpenAPI / Swagger UI
- Docker and Docker Compose
- Maven wrapper (`mvnw`)

## Getting Started

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Maven (optional; the repo includes `mvnw`)

### Quick Start with Docker

```bash
git clone https://github.com/jabezasir421-afk/backend.git
cd backend
docker compose up -d
```

The application will be available at:

- http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html

### Local Development

1. Start the database:

```bash
docker compose up -d postgres
```

2. Run the application:

```bash
./mvnw spring-boot:run
```

The Docker Compose setup includes a PostgreSQL container and application service with the required environment variables for the database and JWT secret.

## Project Structure

```text
src/main/java/com/bluecollar/
├── address/
├── admin/
├── analytics/
├── audit/
├── auth/
├── availability/
├── booking/
├── category/
├── common/
├── customer/
├── notification/
├── portfolio/
├── review/
├── search/
├── skill/
├── storage/
├── worker/
├── BackendApplication.java
└── ...
```

This follows a modular package-by-feature structure, with shared cross-cutting concerns living under `common`.

## Documentation

- Phase 3 architecture and planning: `docs/phase3/`
- Phase 4 production/scale notes: `docs/phase4/`
- Database migrations: `src/main/resources/db/migration/`

## Testing and Quality

Run the test suite:

```bash
./mvnw test
```

Run verification checks and quality analysis:

```bash
./mvnw verify
```

This includes project checks such as JaCoCo, Checkstyle, PMD, SpotBugs, and dependency verification.

## License

This project is currently configured for internal or personal use as defined by the repository owner. See the repository settings or licensing files for details.
