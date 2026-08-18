# Phase 4 – Production & Scale

This phase adds the production-ready foundation for BlueCollar while preserving a modular monolith architecture.

## What is included
- Real-time transport scaffolding with WebSocket support
- Production-grade file validation and storage abstraction
- Security hardening with rate limiting and correlation IDs
- Caching and compression configuration
- Docker and CI/CD scaffolding
- Health and metrics exposure

## Run locally
1. Start PostgreSQL or use Docker Compose:
   - docker compose up -d postgres
2. Build and run the application:
   - ./mvnw spring-boot:run
3. Open the API docs:
   - http://localhost:8080/swagger-ui/index.html

## Docker
- Build image: docker build -t bluecollar-backend .
- Start stack: docker compose up --build

## Notes
- Database-backed tests require a running PostgreSQL instance.
- Production deployment should supply env vars for JWT, DB, and storage settings.
