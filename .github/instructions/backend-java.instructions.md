---
applyTo: "backend/**/*.java"
---
# Backend Java — Expense Analyzer

Use Java 17, Spring MVC, Spring Security, Spring Data JPA, and Flyway. The application is
servlet-based: do not use `Mono`, `Flux`, WebFlux, R2DBC, Reactor, `.block()`,
`subscribe()`, schedulers, or reactive wrappers.

```text
com.expenseanalyzer.<feature>/
├── controller/   # HTTP boundary
├── dto/          # request and response types
├── domain/       # entities, value objects, domain behavior
├── repository/   # Spring Data JPA
└── service/      # use cases and transaction boundaries