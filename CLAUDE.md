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
- **AI**: Spring AI 2.0 (Ollama locally, Anthropic in the cloud) for the posting analyzer (Week 7)

### Database Schema
Flyway scripts in order: V1 `company`, `job_application` · V2 `recruiter` + duplicate/recruiter unique indexes ·
V3/V4 `status_history` · V5 `app_user` (BCrypt `password_hash`, role USER/ADMIN) · V6 `job_application.owner_id`
(NOT NULL) with the unique indexes recreated **per owner** · V7 `status_history.event_id` (unique) · V8 `job_posting`
(unique `url`) · V9 `job_application.reminded_at` · V10 Spring Batch metadata tables (copy of Batch 6.0.5's
`schema-postgresql.sql`; `spring.batch.jdbc.initialize-schema: never`) · V11 `skill` (per user, unique
`(user_id, lower(name))`) · V12 `job_posting.description/external_id/published_at/remote` · V13 `posting_match`
(one AI result per posting and user, status MATCHED/FAILED, analysis as JSONB). Companies, recruiters and job postings are shared between users.

### Security (Week 4)
- Stateless JWT resource server (`config/SecurityConfig`, `config/JwtConfig`). The app signs its own RS256 tokens
  (`auth/TokenService`): `sub` = user id, `roles` = ["USER"|"ADMIN"], `exp` = 1h. Keys (`JwtConfig.loadKeyPair`) either as files
  `jobtracker.jwt.public-key-location/private-key-location` (e.g. `file:/run/secrets/...`) or inline PEM
  `jobtracker.jwt.public-key/private-key` (env `JOBTRACKER_JWT_*`); if neither, a temporary key pair is generated at startup.
- `roles` claim -> `ROLE_USER`/`ROLE_ADMIN` (`SecurityConfig.jwtAuthenticationConverter`); Spring Security 7 also adds `FACTOR_BEARER`.
- Public: `/api/auth/register`, `/api/auth/login`, Swagger UI, `/v3/api-docs`, `/actuator/health`. `DELETE` is ADMIN only,
  except `DELETE /api/skills/{id}` (a user's own skills).
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
  `<topic>.DLT`, e.g. `application-status.DLT` (`KafkaConfig.deadLetterDestination`; Spring Kafka 4's default would be
  `...-dlt`).
- JSON via **`JacksonJsonSerializer`/`JacksonJsonDeserializer`** (Jackson 3, matches Boot 4), no type headers; the
  consumer wraps it in `ErrorHandlingDeserializer`. Don't use the Jackson-2-based `JsonSerializer`.
- Known gap: an event is lost if the app dies between commit and send (no transactional outbox yet).

### Batch & scheduling (Week 6)
- Spring Batch **6** API: `core.job.Job`, `core.step.Step`, `core.job.parameters.*`, item classes under
  `org.springframework.batch.infrastructure.item.*`; steps via `StepBuilder.chunk(n)` → `ChunkOrientedStepBuilder`
  (not the legacy `chunk(n, tm)`); launch via `JobOperator` (not `JobLauncher`). No `@EnableBatchProcessing`
  (it would switch off Boot's auto-configuration and default to an in-memory repository).
- `postingImportJob` (`posting/PostingImportJobConfig`): CSV `title,company,city,url,description` (header; a quoted
  description may span lines: `DefaultRecordSeparatorPolicy`; description required, max 10 000) → `PostingItemProcessor`
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

### AI posting analyzer (Week 7, Spring AI 2.0.1)
- Skills (`skill/`): `/api/skills` CRUD, owner-scoped like applications (another user's skill → 404, duplicate → 409).
  Dev seed: profile `dev` (`SkillSeeder`) gives `jobtracker.seed.skills.owner-email` (application-dev.yaml) the
  skills in `seed/skills.csv` at startup; the user must be registered first (then restart); idempotent, never
  overwrites existing skills. Not active in tests, `local` alone or `cloud`.
- `POST /api/postings/analyze {text}` (max 10 000 chars) → `PostingAnalysis` (title, mustHave, niceToHave,
  matchedSkills, missingSkills, matchScore, summary). `analysis/PostingAnalyzer`: `ChatClient` with the fixed system
  prompt `prompts/posting-analyzer-system.st` and the tool `SkillTools.getMySkills` registered as **defaults**
  (`createChatClient`: `defaultSystem`/`defaultTools`); per call only the posting and `.toolContext(userId)`, `.responseEntity(PostingAnalysis.class)` (entity + token usage).
- Model choice: `spring.ai.model.chat` = `none` (default: app runs, analyzer → 503) · profile `local` = Ollama
  (`docker compose --profile local up -d`, once `docker exec jobtracker-ollama ollama pull qwen2.5:3b`) · profile
  `cloud` = Anthropic, key **only** from env `ANTHROPIC_API_KEY` (never commit keys; `.env` is git-ignored).
- Prompt injection: posting goes verbatim (no template params → `{...}` isn't rendered) between `<posting>` tags it
  can't open/close itself; the answer is checked (score clamped to 0–100, matchedSkills ⊆ the user's skills).
- Resilience: hard timeout `jobtracker.ai.timeout` (30s, virtual-thread future); `spring.ai.retry` max 2 attempts,
  never on 4xx (Spring AI's default is 10 attempts / up to 3 min). Unavailable/timeout/not configured → 503
  ProblemDetail, fixed detail "AI service currently unavailable" + `Retry-After: 30` (the reason is only logged);
  4xx from the provider or unusable answer → 502.
- Metrics (registered at startup, `/actuator/metrics` is exposed and needs a token): log `ai tokens prompt=…
  completion=… total=…` (total as reported by the provider), counter `jobtracker.ai.tokens{type=prompt|completion}`,
  timer `jobtracker.ai.duration{outcome=success|timeout|unavailable|rejected}`.
- Spring AI 2.0 facts: tool calls are executed by the ChatClient's `ToolCallingAdvisor` (not inside the provider
  model); `toolCallbacks(..)` is deprecated → `tools(..)`; the `ChatClient.Builder` bean needs a `ChatModel`, so
  `PostingAnalyzer` checks `ObjectProvider<ChatModel>` first.

### Automatic matching (Week 8)
- `postingMatchJob` (`matching/PostingMatchJobConfig`): every posting with a description × every user with ≥ 1 skill
  without a `posting_match` row (keyset-paged subquery, newest postings first, at most
  `jobtracker.matching.max-per-run`, default 50) → `PostingAnalyzer` as that user → MATCHED row with score + analysis
  JSON. Chunk size 1 (each AI result committed at once). `AiRejectedException` → skipped, stored as FAILED (never
  retried); `AiUnavailableException` → the run fails, the next run continues (no restart state, `saveState(false)`).
- Starts: `MatchingService.start()` (503 without a model, 409 while a run is going) from `POST /api/admin/matching/run`
  (status: `GET /api/admin/matching/{id}`) and after every import (`PostingImportFinishedListener` →
  `PostingsImportedEvent` → `MatchingService.onPostingsImported`, which only logs if it can't start).
- `GET /api/matches?minScore=&page=&size=` → the caller's MATCHED results, best first (`MatchQueryService`, JDBC).
- Job portals (`jobsource/`, `jobtracker.jobs.*`): `JobFetchScheduler` (`fetch-cron`, default 07:00 Berlin, `-` in
  tests) → `JobFetchService.fetchAll()` → every enabled `JobSource` → new postings saved by url
  (`FetchedPostingRepository`, values cut to column sizes) → `PostingsImportedEvent("fetch", n)` → matching.
  A failing portal is reported per source and doesn't stop the others. `POST /api/admin/jobs/fetch` runs it now.
  - `ArbeitnowJobSource`: `GET /api/job-board-api?page=n` (no key, full text included, no search → keyword filter
    locally), stops at `max-per-source` or the last page.
  - `BundesagenturJobSource`: `GET /pc/v6/jobs?was&wo&umkreis&veroeffentlichtseit` with `X-API-Key: jobboerse-jobsuche`
    (list = `ergebnisliste`), then `GET /pc/v4/jobdetails/{base64(referenznummer)}` per hit for
    `stellenangebotsBeschreibung` (v2/v3 answer 403); public page `arbeitsagentur.de/jobsuche/jobdetail/{refnr}`.
  - Tests use hand-written answers in `src/test/resources/jobsource/` via `MockRestServiceServer`; never the real APIs.
- Good matches: `PostingMatchWriter` publishes `GoodMatchFound` for a newly stored score ≥
  `jobtracker.matching.notify-score` (75) → `MatchEvents` (AFTER_COMMIT) → Kafka `posting-matches`, key = user id
  (DLT `posting-matches.DLT`) → `AutoApplicationListener` (group `${spring.kafka.consumer.group-id}-auto-applications`,
  own JSON default type via `@KafkaListener(properties)`) creates a SAVED application (company reused by name or
  created); duplicates are ignored, so redeliveries are harmless. Off with `jobtracker.matching.auto-create-application`.

### Testing
- Testcontainers for integration tests: `TestcontainersConfiguration` = shared static PostgreSQL + Kafka
  (`apache/kafka`, not `kafka-native`, which crashes here) and a Kafka consumer group per Spring context;
  `TopicProbe` reads topics directly in tests. It also sets `jobtracker.reminder.cron` to `-` (scheduler off)
- Batch jobs run async in tests too: await a terminal status via `JobRepository` (see `PostingImportJobTest`).
  Import fixtures: `src/test/resources/postings/` (`valid.csv` 120 rows, `invalid.csv` 3 broken lines,
  `duplicates.csv`); their urls are fixed (`https://fixtures.example/...`), so tests delete those rows first.
  Admin tokens in `@SpringBootTest`: register, `UPDATE app_user SET role = 'ADMIN'`, then `ApiAuth.login`
- AI tests never call a model: `FakeChatModel` (mocked `ChatModel` whose `getOptions()` returns
  `ToolCallingChatOptions`, else tools aren't attached) + the real `ChatClient`, so the tool round trip really runs;
  `@SpringBootTest` uses `@MockitoBean ChatModel` (`spring.ai.model.chat=none` by default).
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