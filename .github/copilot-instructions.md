# Expense Analyzer — Repository Instructions

## Source of truth
Build against the code checked into this repository, not the long-term architecture
described in product documents. When conventions are ambiguous, read the neighboring
feature package and match it. Do not invent packages, layers, or abstractions that do
not already exist in the repo.

## Stack
- Java 17, Spring Boot 4, Spring MVC (servlet), Spring Security, Spring Data JPA,
  Flyway, PostgreSQL.
- Gradle Groovy DSL in `backend/build.gradle` and `backend/settings.gradle`.
- Observability: Actuator, Micrometer Prometheus, OpenTelemetry, structured Logback.
- `frontend/` is unscaffolded — do not assume Next.js, TypeScript, or npm exists.

## Layout
- Sources: `backend/src/main/java/com/expenseanalyzer/`
- Tests:   `backend/src/test/java/com/expenseanalyzer/`
- Migrations: `backend/src/main/resources/db/migration/`
- Feature packages: `account`, `ai`, `analytics`, `auth`, `category`, `enrichment`,
  `insight`, `merchant`, `messaging`, `notification`, `processing`, `statement`,
  `transaction`, `user`. Shared code in `common`.
- Per feature, use `controller/`, `dto/`, `domain/`, `repository/`, `service/`.

## Rules
- Blocking MVC/JPA. Do not add WebFlux, Reactor, R2DBC, Redis, RabbitMQ, Terraform,
  or a frontend framework unless the task explicitly requires and configures it.
- Schema changes require a new Flyway migration. `ddl-auto` is `validate` only. Never
  alter a released migration.
- Never log PII, secrets, statement contents, account numbers, transaction descriptions,
  or amounts. Preserve `X-Correlation-ID` and request-scoped MDC.
- Preserve raw transaction data through import/enrichment. Deterministic calculations
  are authoritative; AI only explains verified data.
- Path-scoped instructions in `.github/instructions/` define Java, test, and migration
  conventions. Follow them for files they match.