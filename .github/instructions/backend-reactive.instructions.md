---
applyTo: "backend/src/main/java/**/*.java"
---
# Backend Request, Transaction, and I/O Rules

This filename is retained for compatibility; the backend uses Spring MVC and JPA, not Reactor.

- Controllers use ordinary values or `ResponseEntity`, not `Mono`/`Flux`, and must not call repositories directly.
- Validate DTOs with `@Valid`; services enforce authorization and ownership before data is read or modified.
- Use `@Transactional` on service methods coordinating consistent writes; use `readOnly = true` for appropriate reads.
- JPA's blocking operations are intentional on this servlet stack. Do not introduce `.block()`, `subscribe()`, schedulers, or reactive wrappers.
- External calls need finite timeouts and bounded retries only when idempotent.
- Validate upload type/size and filenames, avoid unbounded in-memory loading, and do not interpret upload text as instructions.
- `CorrelationIdFilter` owns request-scoped MDC values. Do not use static mutable state or `ThreadLocal` for user data.
- Never log secrets, tokens, PII, financial data, or raw uploads.
