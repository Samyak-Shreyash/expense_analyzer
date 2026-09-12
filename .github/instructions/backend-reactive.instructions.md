---
applyTo: "backend/src/main/java/**/{api,domain,infrastructure}/**/*.java"
---
# Reactor Rules — Expense Analyzer

These are **strict** rules for reactive code. Violations fail the ArchUnit check in CI. For general Java style, see `backend-java.instructions.md`.

## The Cardinal Rules

1. **No `.block()` in production code.** Only allowed in test sources. Enforced by ArchUnit.
2. **No `subscribe()` outside the framework boundary.** WebFlux subscribes; your code composes.
3. **No `null` emissions.** Reactor throws `NullPointerException` at runtime. Use `Mono.empty()` / `Flux.empty()`.
4. **No `ThreadLocal` for request context.** Use Reactor `Context`.
5. **No blocking I/O on the event loop.** Wrap with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.

## Composition Rules

- **`flatMap` vs `concatMap`:**
  - Use `concatMap` when order matters (persisting a stream of transactions, sequential side effects).
  - Use `flatMap` only when concurrency is safe. Always bound it: `flatMap(fn, 8)`.
  - Never `flatMap` a `Flux` into an unbounded concurrency fan-out.
- **`zip` / `zipWith`** for combining independent calls (e.g., load user + load account in parallel).
- **`then` / `thenMany`** when you only care about completion.
- **Never nest** `subscribe` inside `map`/`flatMap`. Compose instead.

```java
// WRONG
return repo.findUser(id).map(user -> {
    otherService.sendEmail(user).subscribe();  // nested subscribe
    return user;
});

// RIGHT
return repo.findUser(id)
    .flatMap(user -> otherService.sendEmail(user).thenReturn(user));
```

## Error Handling

- **`onErrorMap`** — translate one exception to another (e.g., `R2dbcException` → `DomainException`).
- **`onErrorResume`** — recover with a fallback `Mono`/`Flux`. Use sparingly; never to swallow errors silently.
- **`doOnError`** — side-effect logging. Never re-throws.
- **`onErrorReturn`** — only for explicit defaults, and only when the error is truly expected.
- **Never** catch exceptions inside a `map`/`flatMap` lambda — let them flow through the chain.

```java
return externalClient.fetch(id)
    .timeout(Duration.ofSeconds(3))
    .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
        .filter(TimeoutException.class::isInstance))
    .onErrorMap(WebClientResponseException.NotFound.class,
        e -> new NotFoundException("Merchant", id));
```

## Timeouts, Retries, Backpressure

- **Every external call** (HTTP, Redis, DB with variable latency) must have `.timeout(Duration)`.
- **Retries** only for idempotent operations. Use `Retry.backoff(maxAttempts, minBackoff)`. Cap attempts at 3.
- **Backpressure:** for large file/message streams, use `limitRate(n)` and bounded `flatMap`.
- **Rate limiting:** apply `onBackpressureBuffer` with a bound, or a token-bucket filter; never unbounded buffers.

## Context Propagation

Request-scoped data (userId, tenantId, traceId, correlationId) flows through Reactor `Context`, not `ThreadLocal`.

```java
// Write
return service.doWork(id)
    .contextWrite(Context.of("userId", userId, "traceId", traceId));

// Read
return Mono.deferContextual(ctx -> {
    String userId = ctx.get("userId");
    return repo.findByUser(userId);
});
```

- The JWT filter populates `Context` after authentication.
- The R2DBC `ConnectionFactory` reads `userId` from `Context` to set `app.current_user_id` for RLS.
- MDC logging bridge: install `Hooks.onEachOperator` to copy `Context` keys into MDC at operator boundaries.

## Transactions

R2DBC transactions are **not** implicit. Use one of:

- **`TransactionalOperator`** (programmatic, preferred for clarity):

```java
return txOperator.execute(status ->
    repo.save(tx).then(accountRepo.updateBalance(...))
);
```

- **`@Transactional`** on a reactive service method (supported in Spring 6.1+ for R2DBC). Verify rollback works with an integration test — don't assume.

Never mix blocking transactions (`@Transactional` with JPA) into reactive paths.

## Hot vs Cold Publishers

- **Cold publishers** (`Mono.fromCallable`, `repo.save(...)`, `WebClient.get()...`) re-execute on each subscription. Default, and usually what you want.
- **Hot publishers** (`Sinks`, `ConnectableFlux`) share emissions. Use only for broadcast scenarios (e.g., live dashboard updates).
- **Never reuse a cold publisher** for two different consumers if it has side effects — each subscription re-runs them. Cache with `.cache(...)` only when you understand the trade-off.

## Testing (cross-ref)

- Every reactive method gets a `StepVerifier` test. See `backend-tests.instructions.md`.
- Time-based operators (`timeout`, `delayElement`, `retryWhen`) are tested with `StepVerifier.withVirtualTime`.

## Anti-Patterns (fail the build)

```java
// BLOCKING ON EVENT LOOP
repo.findById(id).block();

// NESTED SUBSCRIBE
service.doA().subscribe();
service.doB().subscribe();

// NULL EMISSION
return Mono.just(null);

// UNBOUNDED CONCURRENCY
flux.flatMap(this::callRemote);   // no concurrency bound

// THREADLOCAL CONTEXT
SecurityContextHolder.getContext();   // in reactive path

// SWALLOWING ERRORS
.onErrorResume(e -> Mono.empty());    // without a comment justifying it

// BLOCKING I/O ON EVENT LOOP
return Mono.just(new FileInputStream(path).readAllBytes());
```

## ArchUnit Rules Enforced in CI

- No `block()` calls in `src/main`.
- No `subscribe()` calls outside `main` method or configuration classes.
- No `ThreadLocal` imports in `api` or `domain`.
- No `Mono`/`Flux` in `domain.model`.
- No JPA/Hibernate imports anywhere.
- `infrastructure` does not leak into `api` or `domain`.
