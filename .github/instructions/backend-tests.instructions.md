---
applyTo: "backend/src/test/**/*.java"
---

# Backend Tests — Expense Analyzer

## Framework & Libraries

- **JUnit 5** (`org.junit.jupiter`).
- **AssertJ** for all assertions — never `assertEquals`/`assertTrue` from JUnit.
- **Reactor Test** (`StepVerifier`) for reactive assertions.
- **WebTestClient** for HTTP tests.
- **Mockito** for collaborators. Prefer `@MockitoBean` (Spring 6.2+) over `@MockBean`.
- **Testcontainers** for Postgres and Redis.

## Test Slices

| Slice | Annotation | Purpose |
|---|---|---|
| Controller | `@WebFluxTest(MyController.class)` | Route, serialization, validation, security |
| Repository | `@DataR2dbcTest` + Testcontainers | Custom queries, RLS, constraints |
| Integration | `@SpringBootTest` + `WebTestClient` | End-to-end path through real DB/Redis |
| Unit | Plain JUnit 5 | Pure domain logic, mappers, validators |

**No H2.** R2DBC H2 diverges from Postgres in types, `RETURNING`, JSON, and RLS. Use Testcontainers Postgres.

## Naming

- Class: `<ClassUnderTest>Test` (unit) or `<ClassUnderTest>IT` (integration, optional).
- Method: `methodName_stateUnderTest_expectedBehavior`.
```java
@Test
void categorize_emptyDescription_returnsUncategorized() { ... }

@Test
void findByUser_whenUserHasNoTransactions_returnsEmptyFlux() { ... }
```

## Reactive Tests — Mandatory Patterns

**Always** use `StepVerifier`. Never `.block()`.

```java
@Test
void listForUser_returnsTransactionsInDescendingDateOrder() {
    StepVerifier.create(service.listForUser(userId, filter))
        .expectNextMatches(tx -> tx.occurredAt().isAfter(Instant.now().minusSeconds(60)))
        .expectNextCount(2)
        .verifyComplete();
}
```

**Time-based operators** use virtual time:

```java
@Test
void externalCall_timesOutAfterThreeSeconds() {
    StepVerifier.withVirtualTime(() -> client.fetch(id))
        .thenAwait(Duration.ofSeconds(3))
        .expectError(TimeoutException.class)
        .verify();
}
```

**Empty results:**

```java
StepVerifier.create(repo.findByUser(userId))
    .verifyComplete();   // completes with no emissions
```

**Errors:**

```java
StepVerifier.create(service.get(id))
    .expectErrorSatisfies(e -> assertThat(e)
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Transaction"))
    .verify();
```

## Fixtures

- Build test data with **fixture builders** or **Object Mother** — no shared mutable state.
- Prefer records + `@Builder` in fixtures only (allowlist exception).
```java
public final class TransactionFixtures {
    public static Transaction aTransaction() {
        return aTransaction().withAmount(new Money(new BigDecimal("12.34"), USD));
    }
}
```
- Reset DB between tests via `@Sql` or Testcontainers `@Transactional`. For R2DBC, use `TransactionalOperator` or `DatabaseClient` truncation in `@BeforeEach`.

## Security Tests

- Every new endpoint gets a test proving **unauthenticated requests return 401** and **cross-tenant requests return 404 or 403**.
- RLS must be verified: insert rows for two users, query as one, assert the other's rows are never returned.
- Never bypass security in tests unless explicitly testing the bypass.

## Mocking

- **External services** → WireMock or a stub `WebClient` via `MockWebServer`.
- **Database** → real Postgres via Testcontainers, not mocks. Repository logic is integration territory.
- **Domain services** in controller tests → Mockito.
- **Never mock** what you can instantiate cheaply (value objects, records).

## Coverage

- **JaCoCo.** 80% line coverage on `domain/`. Controllers covered by integration tests, so their unit coverage is not counted against the threshold.
- **Never** write tests solely to hit coverage. Test behavior, not lines.

## Test Data — Safety

- **Never use real PII**, real account numbers, or real card numbers.
- Use synthetic values: `4000-0000-0000-0000`, `user-{uuid}@example.test`.
- Randomized UUIDs for IDs; deterministic for assertions.

## What Every New Production Class Requires

- A new service method → `StepVerifier` unit test.
- A new controller endpoint → `WebTestClient` integration test + auth/tenant test.
- A new custom query → `@DataR2dbcTest` with Testcontainers.
- A new domain model with invariants → unit test covering each invariant.
- A new mapper → unit test with a representative fixture.

## Anti-Patterns

- `.block()` anywhere in tests (use `StepVerifier`).
- `Thread.sleep()` (use `StepVerifier.withVirtualTime`).
- Shared mutable static fixtures.
- Tests that depend on execution order.
- Assertions inside `subscribe()` callbacks (they're never awaited — the test passes vacuously).
- H2 for "speed."
- Mocking repositories when Testcontainers is available.