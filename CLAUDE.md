# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Start PostgreSQL (required before running the app)
docker compose up -d

# Build the project
./mvnw clean package

# Run the application (port 8081)
./mvnw spring-boot:run

# Run tests (uses Testcontainers - Docker required)
./mvnw test

# Run a single test class
./mvnw test -Dtest=JobtrackerApplicationTests

# Run with Testcontainers dev mode (auto-provisions Kafka + Postgres)
./mvnw spring-boot:test-run
```

## Architecture

**Spring Boot 4.1.1 / Java 21** application for tracking job applications.

### Tech Stack
- **Database**: PostgreSQL with Flyway migrations (`src/main/resources/db/migration/`)
- **ORM**: Spring Data JPA with `ddl-auto: validate` (schema managed by Flyway)
- **Security**: Spring Security with OAuth2 Resource Server
- **Messaging**: Apache Kafka
- **Batch**: Spring Batch (jobs disabled by default)
- **AI**: Spring AI with Anthropic/OpenAI integration (dependencies currently commented out)

### Database Schema
Two main entities: `company` and `job_application` (with FK to company). See `V1__init.sql` for schema.

### Testing
- Testcontainers for integration tests (PostgreSQL + Kafka)
- `TestJobtrackerApplication` runs the app with Testcontainers for local development
- Test config in `TestcontainersConfiguration.java`

### Configuration
- App runs on port **8081**
- Docker Compose disabled in app config (manual `docker compose up` required)
- PostgreSQL mapped to port **55432** locally
### Project rules
- Spring Boot 4.1, Java 21, Maven, PostgreSQL, Flyway
- TDD: write/adjust the test first, then the code
- Tests: JUnit 5, AssertJ, Mockito, Testcontainers
- DTOs as Java records, no Lombok
- Package by feature: de.zbidi.jobtracker.<feature>
- Never edit existing Flyway scripts, always add a new V<n>__ script
- Run ./mvnw.cmd test after each change