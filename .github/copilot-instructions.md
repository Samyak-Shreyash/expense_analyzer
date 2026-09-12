# Expense Analyzer — Copilot Instructions

## Project Context

Expense Analyzer is a multi-tenant SaaS platform that ingests user financial transactions (CSV/bank sync), categorizes them automatically, and surfaces spending insights via a web dashboard and API. Primary users are individuals who want transparency into their spending without compromising credentials.

**Non-goals for v1:** Investment tracking, bill pay, native mobile apps, direct credential storage.

**Success metrics (v1):**
- Time-to-first-insight < 2 minutes from CSV upload
- Categorization accuracy ≥ 90% on a held-out validation set
- P95 API latency < 300ms for read endpoints
- 99.9% monthly availability

## Tech Stack

### Backend (Java — Reactive)
- **Language:** Java 21 (LTS) — records, sealed interfaces, pattern matching
- **Framework:** Spring Boot 3.3+ **WebFlux** (Project Reactor)
- **Build:** Gradle 8 (Kotlin DSL), version catalog in `gradle/libs.versions.toml`
- **Persistence:** Spring Data **R2DBC** (reactive) + Flyway for migrations (JDBC, migration-only)
- **Database:** PostgreSQL 16 (multi-tenant with Row-Level Security)
- **Cache/Queue:** Reactive Redis (Lettuce) + RabbitMQ (Reactor RabbitMQ) for ingestion jobs
- **Auth:** Spring Security 6 reactive + JWT (short-lived access + refresh), Argon2 password hashing
- **Validation:** Jakarta Bean Validation on request DTOs
- **API Docs:** springdoc-openapi-webflux-ui (OpenAPI 3.1)
- **Mapping:** MapStruct (compile-time, no reflection)
- **JSON:** Jackson with `JavaTimeModule`

### Frontend
- **Framework:** Next.js 15 (App Router) + TypeScript 5 (strict)
- **Styling:** Tailwind CSS + shadcn/ui
- **State:** Zustand (client) + TanStack Query (server)
- **Forms:** React Hook Form + Zod
- **Charts:** Recharts

### Infrastructure & Tooling
- **Containers:** Docker + Docker Compose (local)
- **CI/CD:** GitHub Actions
- **IaC:** Terraform (AWS ECS Fargate)
- **Observability:** Micrometer + OpenTelemetry, Sentry, Logback with logstash-encoder (JSON logs)
- **Testing:** JUnit 5 + AssertJ + Reactor Test (StepVerifier) + WebTestClient + Testcontainers (Postgres, Redis)

## Project Structure

```
expense-analyzer/
├── backend/
│   ├── src/main/java/com/expenseanalyzer/
│   │   ├── api/                 # Controllers (return Mono/Flux), DTOs, error handling
│   │   │   ├── v1/
│   │   │   ├── dto/
│   │   │   └── error/           # @ControllerAdvice + RFC 7807
│   │   ├── domain/              # Pure business logic — NO reactive types in model
│   │   │   ├── model/           # Records, sealed interfaces, value objects
│   │   │   ├── service/         # Returns Mono/Flux at the boundary
│   │   │   └── port/            # Repository interfaces (return Mono/Flux)
│   │   ├── infrastructure/      # Adapters
│   │   │   ├── persistence/     # R2DBC repositories + entity mappers
│   │   │   ├── messaging/       # Reactor RabbitMQ producers & consumers
│   │   │   └── external/        # WebClient-based external API clients
│   │   ├── config/              # Security, R2DBC, Redis, OpenAPI, Reactor config
│   │   └── ExpenseAnalyzerApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/        # Flyway: V1__init.sql, V2__...
│   ├── src/test/java/...
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle/libs.versions.toml
│   └── Dockerfile
├── frontend/
├── infra/
│   ├── terraform/
│   └── docker/
└── .github/
    ├── copilot-instructions.md
    └── instructions/
```

## Architecture Principles

**Hexagonal / Ports & Adapters.** `domain/model` has zero framework dependencies. `domain/port` defines repository interfaces that return `Mono`/`Flux`. `infrastructure` provides R2DBC, Redis, and WebClient adapters. `api` is the driving adapter.

**Reactive boundary rule:** Reactive types (`Mono`, `Flux`) appear in `api`, `domain/service`, `domain/port`, and `infrastructure`. They **never** appear in `domain/model` — domain models are plain records.

**Modular monolith.** Bounded contexts: `accounts`, `transactions`, `ingestion`, `categorization`, `analytics`, `budgets`. Each context owns its entities, services, and repositories. Cross-context calls go through published service interfaces, never direct repository access.

**Dependency rule:** `api → domain ← infrastructure`. Never `domain → infrastructure`. Never `api → infrastructure` directly (except config wiring).

**Non-blocking rule:** No blocking calls on the event loop. Blocking I/O (JDBC, legacy SDKs, file I/O) must be wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.

## Coding Standards

### Java

- **Records** for domain models, DTOs, value objects, commands, queries.
- **Sealed interfaces** for domain events and result types.
- **Pattern matching** in `switch` and `instanceof` — no casting gymnastics.
- **Optional** only for return types where absence is meaningful. Never as a field or parameter. In reactive code, prefer `Mono.empty()` over `Optional`.
- **No `null` in the reactive chain.** Use `Mono.empty()`.
- **Immutability by default.** `final` fields, `List.copyOf`, `Map.of`.
- **Constructor injection only.** `@RequiredArgsConstructor` + `final` fields. No field injection.
- **Lombok:** `@Slf4j`, `@RequiredArgsConstructor`, `@Value`. Avoid `@Data` and `@Builder` on entities.

### Reactor Conventions (critical)

- **Return `Mono<T>` / `Flux<T>` from controllers and services.** Never return `T` from a reactive endpoint.
- **Never call `.block()`** in production code. Only allowed in tests. Enforced by ArchUnit.
- **Never subscribe** inside a service or controller. Subscriptions happen at the framework boundary or in scheduled jobs.
- **Compose, don't nest.** Use `flatMap`, `concatMap`, `zip`, `zipWith` — never `subscribe()` inside `map()` or `flatMap()`.
- **Use `concatMap` for ordered work.** Use `flatMap` only with a bounded concurrency.
- **Error handling in the chain:** `onErrorResume`, `onErrorMap`, `doOnError`. Never wrap in try/catch inside a `map`/`flatMap`.
- **Context propagation:** Use Reactor `Context` (not `ThreadLocal`) for request-scoped values.
- **Timeouts:** Every external call must have `.timeout(Duration)`.
- **Retries:** Only idempotent operations. Use `Retry.backoff(...)` with a max attempts cap.

**Layering example — Controller → Service → Port → Adapter:**
```java
// api/v1/TransactionController.java
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public Flux<TransactionResponse> list(
            @AuthenticationPrincipal Mono<UserPrincipal> user,
            @Valid TransactionFilter filter) {
        return user.flatMapMany(u ->
                transactionService.listForUser(u.id(), filter)
                        .map(TransactionMapper.INSTANCE::toResponse));
    }
}

// domain/service/TransactionService.java
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public Flux<Transaction> listForUser(UserId userId, TransactionFilter filter) {
        return transactionRepository.findByUser(userId, filter)
                .timeout(Duration.ofSeconds(5));
    }
}

// domain/port/TransactionRepository.java
public interface TransactionRepository {
    Flux<Transaction> findByUser(UserId userId, TransactionFilter filter);
    Mono<Transaction> save(Transaction transaction);
}
```

### Naming Conventions
- Packages: lowercase, singular (`domain.service`).
- Classes: `PascalCase`. Interfaces: no `I` prefix.
- Methods: `camelCase`, verb-first (`findByUser`).
- Constants: `SCREAMING_SNAKE_CASE`.
- DB tables: `snake_case`, plural. Columns: `snake_case`.
- REST paths: plural nouns, kebab-case (`/api/v1/user-accounts`).

## API Conventions

- Base path: `/api/v1`.
- **Controllers return `Mono<T>` or `Flux<T>`** — never `ResponseEntity<T>` unless dynamic status/headers are needed.
- **DTOs are separate from domain models.** Never expose R2DBC entities in controllers.
- **Problem Details (RFC 7807)** via `@ControllerAdvice` with Spring 6 `ProblemDetail`.
- **Pagination:** custom `PageResponse<T>` record wrapping `Flux<T>` + `Mono<Long>` count.
- **Idempotency:** POST endpoints that create resources accept `Idempotency-Key` header.
- **Streaming:** For large exports or live feeds, return `Flux<T>` with `text/event-stream` or `application/x-ndjson`.

## Security Requirements (Non-Negotiable)

- **Never log** financial amounts, account numbers, merchant names, or any PII. Log IDs only.
- **All SQL via Spring Data R2DBC or parameterized `@Query`.** No string concatenation.
- **Row-Level Security** enforced at the database. Set `app.current_user_id` per connection via `ConnectionFactory` decorator.
- **Reactor Context for tenant/user:** propagate `userId` via Reactor `Context`, not `ThreadLocal`.
- **Secrets** from environment variables or AWS Secrets Manager. Never in `application.yml`.
- **CSRF:** Enabled for cookie-based browser flows. Disabled for stateless JWT APIs.
- **CORS:** Explicit allowlist. No `*` in production.
- **File uploads (CSV):** Validate content type, size, sanitize filename. Stream-parse — never load into memory.
- **Dependency scanning:** OWASP Dependency-Check in CI. Fail build on CVSS ≥ 7.

## Testing Guidelines

### Backend
- **Framework:** JUnit 5 + AssertJ + Reactor Test (`StepVerifier`) + WebTestClient + Mockito.
- **Reactive assertions are mandatory.** Never `block()` in tests — use `StepVerifier`:
```java
StepVerifier.create(transactionService.listForUser(userId, filter))
    .expectNextCount(3)
    .verifyComplete();
```
- **Test slices:**
  - `@WebFluxTest` for controllers (mock services).
  - `@DataR2dbcTest` + Testcontainers Postgres for repositories.
  - `@SpringBootTest` + Testcontainers for full integration.
- **Testcontainers** for Postgres and Redis. Never H2.
- **Test naming:** `methodName_stateUnderTest_expectedBehavior`.
- **Time-based operators:** Use `StepVerifier.withVirtualTime(...)`.
- **Coverage:** JaCoCo. 80%+ on `domain/`.

### Frontend
- **Unit/component:** Vitest + React Testing Library.
- **E2E:** Playwright for critical flows (auth, CSV upload, dashboard render).

### What Copilot Should Always Generate
- A `StepVerifier` test for every new reactive service method.
- A `WebTestClient` integration test for every new endpoint.
- A `@DataR2dbcTest` for every new custom query.
- A component test for non-trivial conditional rendering.

## Available Commands

```bash
# Backend
./gradlew bootRun                 # run locally
./gradlew test                    # all tests
./gradlew jacocoTestReport        # coverage
./gradlew spotlessApply           # format
./gradlew check                   # lint + test + ArchUnit
./gradlew flywayMigrate           # apply migrations

# Frontend
pnpm install
pnpm test
pnpm lint
pnpm dev

# Full stack
docker compose up
make test
```

## Patterns to Avoid

- **Don't** call `.block()` in production code. Enforce via ArchUnit test.
- **Don't** use JPA/Hibernate in the request path — it's blocking. Use R2DBC.
- **Don't** return JPA/R2DBC entities from controllers — always map to DTOs.
- **Don't** use `ThreadLocal` for request context — use Reactor `Context`.
- **Don't** use `subscribe()` inside services or controllers.
- **Don't** nest reactive chains — compose with `flatMap`/`concatMap`/`zip`.
- **Don't** use `flatMap` where ordering matters — use `concatMap`.
- **Don't** put reactive types (`Mono`/`Flux`) in `domain/model` records.
- **Don't** use `@Data` on entities.
- **Don't** use field injection (`@Autowired` on fields).
- **Don't** catch `Exception` broadly.
- **Don't** use `System.out.println` — SLF4J (`log.info(...)`).
- **Don't** use `any` in TypeScript or create UI primitives when shadcn/ui covers it.

## Pull Request Requirements

- Title follows Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`).
- Description includes: what changed, why, how tested, migration/breaking changes.
- All CI checks pass (Spotless, ErrorProne/Checkstyle, tests, ArchUnit, Flyway validation).
- At least one approval before merge. Squash merge to `main`.

## Domain-Specific Notes

- **Categorization pipeline:** Deterministic rules first (merchant normalization → regex), then ML/LLM fallback. Never auto-categorize without confidence + audit trail. Model as `sealed interface CategorizationResult`.
- **Transaction deduplication:** SHA-256 hash of `(userId, date, amount, normalizedDescription)`, unique index. Reject duplicates at insert.
- **Monetary values:** `BigDecimal` (`scale=2` for currency, `scale=4` intermediate) or `long` cents. Never `double`/`float`.
- **Timezones:** Store `Instant` (UTC). R2DBC maps to `timestamptz`. API serializes ISO-8601. Frontend renders in user TZ.
- **IDs:** UUID v7 (sortable, distributed-safe). Exposed in API.
- **Domain events:** In-process via Spring `ApplicationEventPublisher`. Cross-service via outbox + Reactor RabbitMQ.
- **Transactions:** R2DBC transactions use `TransactionalOperator` for multi-step writes.

## Domain Model Sketch (for Copilot reference)

```java
public record UserId(UUID value) {}
public record Money(BigDecimal amount, Currency currency) {}
public record TransactionId(UUID value) {}

public record Transaction(
    TransactionId id,
    UserId userId,
    Instant occurredAt,
    Money amount,
    String normalizedDescription,
    CategoryId categoryId,
    CategorizationSource source,
    double confidence
) {}

public sealed interface CategorizationResult
        permits RuleMatch, ModelMatch, Uncategorized {
    record RuleMatch(CategoryId category, String ruleId) implements CategorizationResult {}
    record ModelMatch(CategoryId category, double confidence, String modelVersion) implements CategorizationResult {}
    record Uncategorized() implements CategorizationResult {}
}
```

## Copilot Behavior Expectations

When generating code in this repo, Copilot should:
1. **Default to the layering rule** — controller → service → port → adapter.
2. **Never generate blocking calls** in production code.
3. **Always produce a `StepVerifier` test** alongside reactive production code.
4. **Use records and sealed interfaces** for domain types.
5. **Prefer constructor injection + `@RequiredArgsConstructor`.**
6. **Flag security concerns** — logging PII, SQL construction, tenant isolation, missing timeouts.
7. **Reference ADRs** when architectural choices are involved.