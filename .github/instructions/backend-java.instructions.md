---
applyTo: "backend/**/*.java"
---
# Backend Java — Expense Analyzer

Use Java 17, Spring MVC, Spring Security, Spring Data JPA, and Flyway. The application is servlet-based: do not use `Mono`, `Flux`, WebFlux, R2DBC, or Reactor conventions.

```text
com.expenseanalyzer.<feature>/
├── controller/   # HTTP boundary
├── dto/          # request and response types
├── domain/       # entities, value objects, domain behavior
├── repository/   # Spring Data JPA
└── service/      # use cases and transaction boundaries
```

- Use records for immutable DTOs/value objects where JPA does not require an entity; use classes for JPA entities.
- Use 4-space indentation, no wildcard imports, constructor injection, and `private final` dependencies. No field injection or Lombok `@Data`.
- Controllers use `@Valid`, delegate to services, and return DTOs or `ResponseEntity`. They contain no business/persistence logic.
- Services own `@Transactional` boundaries; repositories only express persistence operations. Do not expose entities in API responses.
- Use `Optional` only as a return type. Do not return `null` when an empty result fits.
- Use `BigDecimal` for money and `Instant` for event timestamps.
- Enforce authenticated-user ownership. Never concatenate untrusted input into JPQL/SQL, paths, or logs.
- Do not log PII, secrets, financial details, or raw statement content. Keep `spring.jpa.open-in-view=false` and load required data in services.
