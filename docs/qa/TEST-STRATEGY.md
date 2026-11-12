# Expense Analyzer — QA Strategy

_Version 1.0 · 2026-11-12 · Horizon: through MVP launch + 2 quarters_

**The decision:** keep the strong backend suite as the base. Add four things it lacks: frontend in CI, coverage measurement, a thin Playwright E2E layer over the 6 critical journeys, and Postgres-backed integration tests for money logic. Today every suite is green (backend 647, web 132, mobile 47). The risk is in what isn't tested, not in what is failing.

## 1. Scope

| In scope | Out of scope (for now) |
|---|---|
| Backend REST API (9 controllers, 41 endpoints) | Native device-farm testing (until mobile ships to stores) |
| Statement parsing: CSV, Excel, PDF, GPay | Load testing beyond the targets in SUCCESS-METRICS |
| Analytics correctness, dedupe, transfers, reversals | AI/LLM features: `ai/` packages are empty (MVP+). See §7 |
| Auth, ownership, account deletion, log redaction | Cross-browser beyond Chromium + WebKit |
| Web (React/Vite) and mobile (Expo) clients | |
| Outbox → Kafka pipeline, S3 storage, Flyway migrations | |

## 2. Risk ranking (impact × likelihood, 1–5 each)

| # | Risk | I | L | Score | Current coverage | Gap |
|---|---|---|---|---|---|---|
| R1 | Wrong totals or analytics (calculation must be 100%) | 5 | 3 | 15 | Analytics controller tests on H2 | H2 ≠ Postgres for numeric/date/aggregate SQL |
| R2 | Cross-user data leak | 5 | 2 | 10 | Strong: CrossUserIsolation, AuthorizationMatrix | Not run on Postgres; no E2E check |
| R3 | Parser misreads a bank format (≥98% accuracy target) | 4 | 4 | 16 | Unit tests per parser | No golden-file corpus with an accuracy score |
| R4 | Duplicate transactions on re-upload (100% target) | 4 | 3 | 12 | DuplicateTest, TransactionFingerprintTest | No re-upload E2E |
| R5 | Schema drift: tests use `create-drop`, prod uses Flyway | 4 | 3 | 12 | One FlywaySchemaValidationTest | Most integration tests never see the real schema |
| R6 | Web regressions merge unnoticed | 3 | 4 | 12 | 132 Vitest tests run locally only | Frontend not in CI |
| R7 | Web ↔ API contract drift (shared `api-client`) | 3 | 3 | 9 | None | No contract or schema test |
| R8 | Token or PII in logs | 5 | 1 | 5 | SensitiveLogRedactor + integration test | Fine. Keep it. |
| R9 | Accessibility failures | 2 | 3 | 6 | None | No axe checks |

Top priorities: **R3, R1, R4/R5/R6.**

## 3. Test levels and target mix

| Level | Tool | Owns | Target share | Runs |
|---|---|---|---|---|
| Unit | JUnit 5 / Vitest / Jest | Parsers, normalizers, fingerprinting, formatters, hooks | ~65% | Every PR |
| Integration (backend) | Spring Boot Test + Testcontainers **Postgres** | Repositories, analytics SQL, security matrix, outbox | ~25% | Every PR |
| Component (web/mobile) | Testing Library | Pages, forms, error states | (in unit share) | Every PR |
| Contract | OpenAPI schema check of `api-client` vs backend | Request/response shapes | small | Every PR |
| E2E | Playwright (web), Maestro (mobile, later) | 6 critical journeys only | ~5–10 tests | PR (smoke) + nightly (full) |
| Golden-file accuracy | JUnit parameterized over `testdata/statements/` | Parser accuracy % per bank | 1 suite | Every PR |
| Non-functional | k6 (`../loadtest-backend`), axe-core, ZAP baseline | Perf targets, WCAG 2.2 AA, OWASP | — | Nightly / pre-release |

**Rule:** E2E proves the wiring; correctness lives in unit and integration tests. Don't add an E2E test for anything a lower level can check.

## 4. Key decisions

1. **Move money-path tests to Postgres.** Analytics, transaction repository, dedupe and the security matrix run on Testcontainers Postgres with Flyway enabled. Keep H2 only for fast slice tests that use no SQL dialect features. This closes R1, R2 and R5 together.
2. **Golden statement corpus.** Each supported format gets anonymized sample files with expected output in JSON. The suite reports field-level accuracy and fails below 98% for CSV. Turns a product KPI into a test gate.
3. **Coverage as a ratchet, not a target.** Add JaCoCo and `vitest --coverage`. Record today's baseline and fail a PR only if it lowers coverage on changed files.
4. **Playwright for web E2E**, running against the docker-compose stack. Use role/label locators (same as Testing Library). Seed data through the API, never the database directly.
5. **Polling, not sleeping.** Replace the 3 `Thread.sleep` calls with Awaitility (`StatementPipelineE2ETest` polls every 250 ms).

## 5. Quality gates

| Gate | Blocks merge to `main` | Blocks release |
|---|---|---|
| Backend build + tests | yes | yes |
| Frontend lint + typecheck + Vitest | yes (new) | yes |
| Mobile `npm run ci` | yes | yes |
| Coverage on changed lines doesn't drop | yes (new) | — |
| Parser golden-file accuracy ≥98% CSV | yes (new) | yes |
| CodeQL Java + **JS/TS** high/critical = 0 | yes | yes |
| E2E smoke (6 journeys) | — | yes |
| Dependency audit: no high/critical | yes | yes |
| k6 p95 API < 500 ms at expected load | — | yes |
| axe: 0 serious/critical on 6 core pages | — | yes |

## 6. Metrics we track

Flaky rate (target <1% of runs), PR suite duration (target backend <3 min; today 1m29s locally), escaped defects per release, parser accuracy per format, coverage trend. Product KPIs come from `docs/product/SUCCESS-METRICS.md` and are checked in production through the existing Prometheus/Grafana stack.

## 7. When AI ships (MVP+)

Before any `ai/` feature merges:
- An eval set of 50+ real questions with expected tool calls.
- Assert tool-call validity and that every number in an answer appears in tool results. Deterministic analytics stay the authority.
- A red-team set for prompt injection through transaction descriptions.
- Track latency and cost per query.

Use the `ai-system-testing` skill at that point.

## 8. Roadmap

| When | Deliver |
|---|---|
| Weeks 1–2 | Frontend in CI; fix `main.yml` placeholder; CodeQL JS/TS; JaCoCo + Vitest coverage baseline |
| Weeks 3–4 | Golden statement corpus; move analytics/dedupe/security tests to Postgres; Awaitility |
| Weeks 5–6 | Playwright + 6 journey smoke tests; API contract check |
| Q+1 | axe in E2E; k6 nightly; Maestro mobile smoke; release checklist |
| MVP+ | AI eval harness (§7) |
