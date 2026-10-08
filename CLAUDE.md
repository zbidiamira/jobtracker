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

# Run with Testcontainers dev mode (auto-provisions Postgres + Kafka)
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
Flyway scripts in order: V1 `company`, `job_application` · V2 `recruiter` + duplicate/recruiter unique indexes ·
V3/V4 `status_history` · V5 `app_user` (BCrypt `password_hash`, role USER/ADMIN) · V6 `job_application.owner_id`
(NOT NULL) with the unique indexes recreated **per owner**. Companies and recruiters are shared between users.

### Security (Week 4)
- Stateless JWT resource server (`config/SecurityConfig`, `config/JwtConfig`). The app signs its own RS256 tokens
  (`auth/TokenService`): `sub` = user id, `roles` = ["USER"|"ADMIN"], `exp` = 1h. Keys (`JwtConfig.loadKeyPair`) either as files
  `jobtracker.jwt.public-key-location/private-key-location` (e.g. `file:/run/secrets/...`) or inline PEM
  `jobtracker.jwt.public-key/private-key` (env `JOBTRACKER_JWT_*`); if neither, a temporary key pair is generated at startup.
- `roles` claim -> `ROLE_USER`/`ROLE_ADMIN` (`SecurityConfig.jwtAuthenticationConverter`); Spring Security 7 also adds `FACTOR_BEARER`.
- Public: `/api/auth/register`, `/api/auth/login`, Swagger UI, `/v3/api-docs`, `/actuator/health`. `DELETE` is ADMIN only.
  Admins can't self-register; promote a user in the database.
- Ownership: controllers pass `CurrentUser.id(jwt)` (the `sub` claim; non-numeric → 401) as `ownerId` to every service
  call; the search Specification always includes `ownedBy(ownerId)`; repository lookups are `...AndOwnerId`, so another
  user's application is a **404**, never a 403. Status changes need the `version` from the last read (409 if stale).
- 401/403 are ProblemDetail like all other errors (`common/GlobalExceptionHandler`).

### Events (Week 5, Kafka)
- `docker compose up -d` starts PostgreSQL **and Kafka** (`apache/kafka`, KRaft, `localhost:9092`); the app connects via
  `spring.kafka.bootstrap-servers` (env `KAFKA_BOOTSTRAP_SERVERS`), since Docker Compose support is disabled.
- Status change → `StatusChangedEvent` (Spring event) → `StatusEventPublisher` sends it **after commit**
  (`@TransactionalEventListener(AFTER_COMMIT)`) to topic `application-status`, key = application id →
  `StatusHistoryListener` writes `status_history`, idempotent via unique `event_id` (V7).
- History is **eventually consistent** (tests wait with Awaitility). Failures: 3 retries with backoff, then
  `application-status.DLT` (set explicitly in `KafkaConfig`; Spring Kafka 4's default name would be `...-dlt`).
- JSON via **`JacksonJsonSerializer`/`JacksonJsonDeserializer`** (Jackson 3, matches Boot 4), no type headers; the
  consumer wraps it in `ErrorHandlingDeserializer`. Don't use the Jackson-2-based `JsonSerializer`.
- Known gap: an event is lost if the app dies between commit and send (no transactional outbox yet).

### Testing
- Testcontainers for integration tests: `TestcontainersConfiguration` = shared static PostgreSQL + Kafka
  (`apache/kafka`, not `kafka-native`, which crashes here) and a Kafka consumer group per Spring context;
  `TopicProbe` reads topics directly in tests
- `TestJobtrackerApplication` runs the app with Testcontainers for local development
- Test config in `TestcontainersConfiguration.java`
- `@WebMvcTest`: `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` and authenticate requests with
  `TestJwt.user(id)` / `TestJwt.admin(id)`. `@SpringBootTest`: get real tokens via `ApiAuth.registerNewUser(mvc, ...)`.
- Time-dependent code uses the `Clock` bean (Europe/Berlin); tests use `MutableClock` via `TestClockConfiguration`.

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