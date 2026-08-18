# BlueCollar Backend Service

BlueCollar is a production-ready marketplace platform that connects skilled blue-collar workers with customers. This backend service provides the core APIs for service discovery, booking management, worker portfolios, and real-time notifications.

---

## 🚀 Key Features

### 👤 Worker & Customer Management
- **Professional Portfolios:** Workers can showcase their work, certificates, and identity verification documents.
- **Dynamic Skill Sets:** Multi-category skill management (Plumbing, Electrical, Carpentry, etc.).
- **Worker Availability:** Real-time online status, shift management, and vacation scheduling.

### 📅 Booking & Service Flow
- **Service Discovery:** Advanced search with filtering by city, category, skill, and worker ratings.
- **Booking Lifecycle:** Secure booking flow from request to completion.
- **Review System:** Comprehensive rating and review system with moderation and reporting capabilities.

### 🛠 Platform Infrastructure
- **Secure Authentication:** JWT-based stateless authentication with Refresh Token support.
- **Real-time Notifications:** In-app and email notification system.
- **Analytics & Trends:** Daily snapshots of platform metrics and worker rankings.
- **Audit Logging:** System-wide auditing for sensitive operations.
- **File Storage:** Abstracted file storage for profile pictures, portfolios, and documents.

---

## 🏗 Tech Stack

- **Framework:** Spring Boot 3.4.x (Java 21)
- **Database:** PostgreSQL 16
- **Migration:** Flyway
- **Security:** Spring Security + JWT
- **API Documentation:** OpenAPI / Swagger UI
- **DevOps:** Docker & Docker Compose
- **Quality Assurance:** JUnit 5, Testcontainers, JaCoCo, Checkstyle, PMD, SpotBugs

---

## 🚦 Getting Started

### Prerequisites
- **Java 21** or higher
- **Docker** and **Docker Compose**
- **Maven** (or use the included `mvnw`)

### Quick Start with Docker
```bash
# Clone the repository
git clone https://github.com/BlueCollar/backend.git
cd backend

# Start the database and application
docker-compose up -d
```
The application will be available at `http://localhost:8080`.

### Local Development
1. Start the database only:
   ```bash
   docker-compose up -d postgres
   ```
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## 📚 Documentation & API

- **API Reference:** Once the app is running, visit `http://localhost:8080/swagger-ui.html`
- **Architecture Details:** See the [docs/](./docs) folder for phase-specific designs and ER diagrams.
- **Database Schema:** Managed via Flyway migrations in `src/main/resources/db/migration`.

---

## 🧪 Testing & Quality

Run the full test suite including integration tests using Testcontainers:
```bash
./mvnw test
```

Generate quality reports:
```bash
./mvnw verify
```
Reports are available in `target/site/`:
- **JaCoCo:** Code coverage
- **Checkstyle/PMD:** Static analysis
- **SpotBugs:** Bug patterns

---

## 📂 Project Structure

```text
src/main/java/com/bluecollar/
├── analytics/    # Platform metrics and rankings
├── availability/ # Worker schedules and status
├── booking/      # Service request management
├── common/       # Cross-cutting concerns (Security, Exception handling)
├── notification/ # Email and in-app alerts
├── portfolio/    # Worker skills and identity
├── review/       # Ratings and moderation
├── search/       # Advanced filtering logic
└── storage/      # File upload and metadata
```

---
*Kindly refer to `docs/phase3` and `docs/phase4` for detailed implementation specs.*
