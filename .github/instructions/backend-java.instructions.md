---
applyTo: "backend/**/*.java"
---
# Backend Java — Expense Analyzer

These rules apply to all Java files under `backend/`. For stricter Reactor-specific rules, see `backend-reactive.instructions.md`. For tests, see `backend-tests.instructions.md`.

## Language & Style

- **Java 21.** Use records, sealed interfaces, pattern matching, text blocks, `var` for local variables when the type is obvious.
- **4-space indentation.** No tabs. Max line length 120.
- **Import order:** static imports first, then `java.*`, `javax.*`, `jakarta.*`, `org.*`, `com.*`, then project imports. No wildcard imports (except static test assertions).
- **No `var` for fields, method parameters, or return types** — only locals.
- **Braces always required**, even for single-statement `if`/`for`.

## Types & Immutability

- **Records** for DTOs, value objects, commands, queries, events, and domain models.
- **Sealed interfaces + records** for closed hierarchies:

```java
public sealed interface CategorizationResult
        permits RuleMatch, ModelMatch, Uncategorized {}
```

- **`final` on all fields.** Unmodifiable collections: `List.copyOf`, `Map.of`, `Set.of`.
- **No `null` for absent values.** Use `Optional<T>` for return types only, or model absence as a sealed variant. Never `Optional` as a field or parameter.
- **Never return `null`** from a method whose signature says otherwise. Prefer `Optional.empty()`, empty collections, or `Mono.empty()` (reactive paths).

## Objects & Classes

- **Constructor injection only.** `@RequiredArgsConstructor` + `private final` fields. No field injection, no setter injection.
- **Classes are `final` unless designed for extension.** No `protected` members unless subclassing is intended.
- **Equals/HashCode:** Let records generate them. For non-records, use `Objects.equals`/`Objects.hash`. Never `@Data` on R2DBC entities.
- **Lombok allowlist:** `@Slf4j`, `@RequiredArgsConstructor`, `@Value`, `@Builder` (only on test fixtures or non-entity DTOs). **Banned:** `@Data`, `@EqualsAndHashCode` on entities, `@SneakyThrows`.

## Layering (enforce via ArchUnit)

- `api` depends on `domain`.
- `infrastructure` depends on `domain`.
- `domain` depends on nothing (no Spring, no R2DBC, no WebFlux).
- `domain.model` contains **no reactive types** (`Mono`, `Flux`).
- Controllers never import `infrastructure.*`.
- Services never import `api.*`.

If code doesn't fit a layer, put it in `domain` and let adapters call it.

## Controllers (`api/v1`)

- `@RestController` + `@RequestMapping("/api/v1/<resource>")`.
- Return `Mono<T>`, `Flux<T>`, or `Mono<ResponseEntity<T>>` — never block.
- Read principal via `@AuthenticationPrincipal Mono<UserPrincipal>`.
- Validate input with `@Valid` on request DTOs. Never validate manually in the handler.
- Map domain → DTO with MapStruct. Do not hand-write mappers.
- **No business logic.** Controller = validate → delegate → map → return.
- For streaming responses use `Flux<T>` with `produces = MediaType.TEXT_EVENT_STREAM_VALUE` or `APPLICATION_NDJSON_VALUE`.

```java
@GetMapping
public Flux<TransactionResponse> list(
        @AuthenticationPrincipal Mono<UserPrincipal> user,
        @Valid TransactionFilter filter) {
    return user.flatMapMany(u ->
            service.listForUser(u.id(), filter).map(TransactionMapper.INSTANCE::toResponse));
}
```

## Services (`domain/service`)

- `@Service` + `@RequiredArgsConstructor`.
- Return `Mono`/`Flux` at the boundary. Internal helpers may return plain values if they're pure.
- **Never call `.block()` or `subscribe()`.**
- **Never use `ThreadLocal`** — use Reactor `Context`.
- Add `.timeout(Duration)` to every external call.
- Use `TransactionalOperator` for multi-step reactive writes.
- Throw domain exceptions (`NotFoundException`, `ValidationException`, `DomainException`) — never `RuntimeException` directly.

## Repositories / Ports (`domain/port`)

- Ports are interfaces owned by the domain. Return `Mono`/`Flux`.
- No R2DBC, JPA, or SQL annotations in ports.
- One port per aggregate (e.g., `TransactionRepository`, `AccountRepository`).

## Infrastructure (`infrastructure/*`)

- **Persistence adapters** implement ports, backed by Spring Data R2DBC repositories.
- **Entity ↔ domain mapping** via MapStruct — never expose R2DBC entities outside `infrastructure.persistence`.
- **External clients** use `WebClient`, configured with timeouts, retries (idempotent only), and a Reactor Netty connection pool.
- **Messaging** via Reactor RabbitMQ — publish domain events only after the DB write commits (outbox pattern).

## Error Handling

- All errors map to RFC 7807 `ProblemDetail` via `@ControllerAdvice`.
- Domain exceptions extend a common base:

```java
public abstract class DomainException extends RuntimeException {
    private final String type;   // problem URI suffix, e.g. "not-found"
    private final HttpStatus status;
}
```

- **Do not catch `Exception` broadly.** Catch specific types; let the advice handle the rest.
- **Do not swallow exceptions** in reactive chains — use `onErrorMap` to translate, `onErrorResume` only for genuine fallbacks.

## Logging

- SLF4J only. **No `System.out`, `System.err`, `printStackTrace()`.**
- **Never log:** amounts, account numbers, merchant names, descriptions, emails, tokens, request bodies with PII.
- Log IDs and correlation IDs only:

```java
log.info("Ingestion completed txId={} userId={} count={}", txId, userId, count);
```

- Use parameterized logging — never string concatenation.
- MDC is thread-bound; under Reactor use `contextWrite` + a `Hooks.onEachOperator` MDC bridge. Prefer Reactor `Context` for request-scoped data.

## Money & Time

- **Money:** `BigDecimal` (`scale=2` for currency, `scale=4` intermediate) or `long` cents. **Never `double`/`float`.**
- **Time:** `Instant` for storage, `OffsetDateTime`/`ZonedDateTime` for presentation only. All DB columns `timestamptz`.

## Annotations Order

Class-level annotations, in this order: Spring stereotypes (`@Service`, `@RestController`), Lombok (`@RequiredArgsConstructor`, `@Slf4j`), validation, Jackson.

## What Copilot Must Never Generate

- `.block()` or `.subscribe()` in `main` sources.
- `@Autowired` on fields.
- `@Data` on R2DBC entities.
- `ThreadLocal` for request context.
- `SELECT *` or string-concatenated SQL.
- JPA/Hibernate imports anywhere.
- `catch (Exception e) {}` empty blocks.
- Hardcoded secrets, URLs, or credentials.
