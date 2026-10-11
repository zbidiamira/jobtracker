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
- **Batch**: Spring Batch 6 with a JDBC job repository (`spring-boot-starter-batch-jdbc`); jobs don't run at startup
- **AI**: Spring AI with Anthropic/OpenAI integration (dependencies currently commented out)

### Database Schema
Flyway scripts in order: V1 `company`, `job_application` · V2 `recruiter` + duplicate/recruiter unique indexes ·
V3/V4 `status_history` · V5 `app_user` (BCrypt `password_hash`, role USER/ADMIN) · V6 `job_application.owner_id`
(NOT NULL) with the unique indexes recreated **per owner** · V7 `status_history.event_id` (unique) · V8 `job_posting`
(unique `url`) · V9 `job_application.reminded_at` · V10 Spring Batch metadata tables (copy of Batch 6.0.5's
`schema-postgresql.sql`; `spring.batch.jdbc.initialize-schema: never`). Companies, recruiters and job postings are
shared between users.

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

### Batch & scheduling (Week 6)
- Spring Batch **6** API: `core.job.Job`, `core.step.Step`, `core.job.parameters.*`, item classes under
  `org.springframework.batch.infrastructure.item.*`; steps via `StepBuilder.chunk(n)` → `ChunkOrientedStepBuilder`
  (not the legacy `chunk(n, tm)`); launch via `JobOperator` (not `JobLauncher`). No `@EnableBatchProcessing`
  (it would switch off Boot's auto-configuration and default to an in-memory repository).
- `postingImportJob` (`posting/PostingImportJobConfig`): CSV `title,company,city,url` (header) → `PostingItemProcessor`
  (trim/validate → `ValidationException` = skip; known or repeated url → `null` = filter) → `JdbcBatchItemWriter`
  (`ON CONFLICT (url) DO NOTHING`). Chunk 50, skip `FlatFileParseException`/`ValidationException`, limit 10
  (11th fails the job), `PostingSkipLogger` logs skipped lines. Identifying params: `input.file` + `run.date`.
- Async: the `@BatchTaskExecutor` bean (`config/BatchConfig`, virtual threads) makes Boot's JobOperator return at once.
- API (ADMIN, `/api/admin/**`): `POST /api/admin/imports` (multipart `file`) → 202 + Location;
  `GET /api/admin/imports/{executionId}`; `POST /api/admin/imports/{executionId}/restart` continues a FAILED run after
  the last committed chunk (409 if not restartable). Uploads stay in `jobtracker.imports.directory` for restarts.
- Reminders (`reminder/`): `ReminderScheduler` (`jobtracker.reminder.cron`, default 08:00 Europe/Berlin) →
  `ReminderService`: APPLIED + `reminded_at IS NULL` + `updated_at` older than `jobtracker.reminder.after` (14d) →
  conditional JDBC update of `reminded_at` (not via JPA: no `@Version`/`updated_at` bump) → `ReminderEvent` → after
  commit to topic `application-reminders` (key = application id). No consumer yet. `findOverdue()` uses the injected
  `Clock` (`ReminderServiceTest` fixes it); the exact cutoff boundary is tested in `ReminderRepositoryTest`.
  Multi-instance safe without a lock: `markReminded` only succeeds while `reminded_at IS NULL`.
- Restart: starting with the same identifying parameters after a FAILED run restarts that JobInstance (same as
  `JobOperator.restart`); the reader resumes from `postingReader.read.count` (an int) in the step ExecutionContext.
  `PostingImportRestartTest` (writer fails once on chunk 2) and `restartContinuesAfterTheLastCommittedChunk` cover it.
- Gotchas: the JDBC `JobRepository.getJobExecution(id)` throws `EmptyResultDataAccessException` for unknown ids;
  executions loaded from it have no exception objects, only exit descriptions (stack-trace text, see
  `ImportStatusResponse.failures`);
  `@SpringBatchTest` picks *any* test method returning `StepExecution`/`JobExecution` as its scope factory, so batch
  tests define `getStepExecution()`/`getJobExecution()`.

### Testing
- Testcontainers for integration tests: `TestcontainersConfiguration` = shared static PostgreSQL + Kafka
  (`apache/kafka`, not `kafka-native`, which crashes here) and a Kafka consumer group per Spring context;
  `TopicProbe` reads topics directly in tests. It also sets `jobtracker.reminder.cron` to `-` (scheduler off)
- Batch jobs run async in tests too: await a terminal status via `JobRepository` (see `PostingImportJobTest`).
  Import fixtures: `src/test/resources/postings/` (`valid.csv` 120 rows, `invalid.csv` 3 broken lines,
  `duplicates.csv`); their urls are fixed (`https://fixtures.example/...`), so tests delete those rows first.
  Admin tokens in `@SpringBootTest`: register, `UPDATE app_user SET role = 'ADMIN'`, then `ApiAuth.login`
- `TestJobtrackerApplication` runs the app with Testcontainers for local development
- Test config in `TestcontainersConfiguration.java`
- `@WebMvcTest`: `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` and authenticate requests with
  `TestJwt.user(id)` / `TestJwt.admin(id)`. `@SpringBootTest`: get real tokens via `ApiAuth.registerNewUser(mvc, ...)`.
- Time-dependent code uses the `Clock` bean (Europe/Berlin); tests use `MutableClock` via `TestClockConfiguration`.

### Configuration
- App runs on port **8081**
- Docker Compose disabled in app config (manual `docker compose up` required)
- PostgreSQL mapped to port **55432** locally
- Maven needs a JDK 21: if `JAVA_HOME` points to the old JDK 8 / a JRE ("No compiler is provided"), use
  `JAVA_HOME=~/.jdks/temurin-21.0.12.1`
### Project rules
- Spring Boot 4.1, Java 21, Maven, PostgreSQL, Flyway
- TDD: write/adjust the test first, then the code
- Tests: JUnit 5, AssertJ, Mockito, Testcontainers
- DTOs as Java records, no Lombok
- Package by feature: de.zbidi.jobtracker.<feature>
- Never edit existing Flyway scripts, always add a new V<n>__ script
- Run ./mvnw.cmd test after each change