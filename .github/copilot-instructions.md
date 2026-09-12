# Expense Analyzer — Repository Instructions

## Source of truth

Build against the code checked into this repository, not the long-term architecture
described in product documents.

- The code in this repo is authoritative. If `docs/` describes a target architecture
  that is not yet implemented, do not generate code for it unless explicitly asked.
- When conventions are ambiguous, read the neighboring feature package and match it.
- Do not invent packages, layers, or abstractions that do not already exist in the repo.

## Current stack

- Backend: Java 17, Spring Boot 4, Spring MVC (servlet), Spring Security, Spring Data JPA,
  Flyway, PostgreSQL.
- Build: Gradle Groovy DSL in `backend/build.gradle` and `backend/settings.gradle`.
- Observability: Actuator, Micrometer Prometheus, OpenTelemetry tracing, structured
  Logback logging.
- Local services: `infrastructure/compose/compose.yaml` provisions PostgreSQL, the
  backend, and optional observability components.
- Frontend: `frontend/` contains only `.gitkeep`; do not assume Next.js, TypeScript, or
  npm tooling exists.

## Project structure

```text
expense_analyzer/
├── backend/
│   ├── build.gradle, settings.gradle, Dockerfile
│   └── src/
│       ├── main/java/com/expenseanalyzer/
│       │   ├── <feature>/       # account, auth, statement, transaction, etc.
│       │   ├── common/          # shared cross-cutting code
│       │   ├── config/
│       │   └── observability/
│       ├── main/resources/application.yml
│       ├── main/resources/db/migration/
│       └── test/
├── docs/                        # product, API, architecture, domain, events, backlog
├── frontend/                    # reserved, unscaffolded
├── infrastructure/
│   ├── compose/compose.yaml
│   ├── docker/backend/Dockerfile
│   ├── observability/
│   └── scripts/
└── .github/
    ├── workflows/
    └── instructions/
```

Feature packages already reserved are `account`, `ai`, `analytics`, `auth`, `category`,
`enrichment`, `insight`, `merchant`, `messaging`, `notification`, `processing`,
`statement`, `transaction`, and `user`. Use the feature's existing `controller`, `dto`,
`domain`, `repository`, and `service` packages where relevant. Keep shared code in
`common`.

## Rules

- This is a blocking MVC/JPA application. Do not add WebFlux, Reactor, R2DBC, Redis,
  RabbitMQ, Terraform, or a frontend framework unless the task explicitly requires and
  configures it.
- Controllers validate and authorize requests, delegate to services, and return DTOs;
  do not expose entities or call repositories directly.
- Services own use-case orchestration and `@Transactional` boundaries. Repositories
  remain persistence-focused.
- Use constructor injection, Jakarta Bean Validation, `BigDecimal` for money, and
  parameterized/JPA queries.
- Enforce ownership for every user-scoped read and write. Never log PII, secrets,
  financial statement contents, account numbers, transaction descriptions, or amounts.
- Preserve raw transaction data through import/enrichment. Deterministic calculations
  are authoritative; AI only explains verified data.
- Preserve the existing safe `X-Correlation-ID` and request-scoped MDC behavior.
- Schema changes require new Flyway migrations; `ddl-auto` is validation only. Never
  alter an already released migration.
- Use JUnit 5 and AssertJ. The test profile uses H2; only require other test tooling
  after adding it. Run `./gradlew.bat test` from `backend/` on Windows.

# Java Development Guidelines

## Code Style
- Follow Google Java Format. Use 4-space indentation.
- Use `PascalCase` for classes/interfaces, `camelCase` for methods/variables, `UPPER_SNAKE_CASE` for constants.
- Private fields by default. Expose via getters/setters only when necessary.
- Prefer `final` for local variables and parameters that don't change.

## Architecture & Patterns
- Use constructor injection over field injection.
- Use DTOs for API request/response payloads. Never expose JPA entities directly.
- Use `@ControllerAdvice` for global exception handling.
- Log with SLF4J using parameterized logging: `log.info("User {} created", userId)`.

## Null Safety & Validation
- Always check for null. Use `Optional<T>` for return values that may be empty.
- Validate inputs with `@Valid` and Bean Validation annotations.

## Testing
- Use JUnit 5 and Mockito. Follow Arrange-Act-Assert.
- Test edge cases, null inputs, and exception flows, not just the happy path.

## Avoid
- Field injection (`@Autowired` on fields).
- Catching and ignoring exceptions.
- Hardcoding values—use `@Value` or `application.yml`.
- God classes or business logic in controllers.