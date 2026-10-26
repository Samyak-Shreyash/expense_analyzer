# Issue #122 Plan: Sensitive-Log Redaction

Issue: #122

## Goal
Passwords, tokens, secrets, raw transaction descriptions and full statements never reach log output. Redaction is applied at the logging layer (defence in depth) and the one known direct leak is removed at source.

## Global Constraints
- Java 21, Spring Boot 4.1.1, Gradle from `backend/` (`./gradlew.bat` on Windows). Edit only `backend/src/`. No new dependencies (logstash-logback-encoder 7.4 is already in `build.gradle:9,62`; Boot's built-in structured logging is used: `logging.structured.format.console: logstash` in `application.yml` and `src/test/resources/application.yml`).
- No `logback-spring.xml` exists; do NOT add one. Hook in via Boot's `logging.structured.json.customizer` property (a `StructuredLoggingJsonMembersCustomizer<ILoggingEvent>`), which keeps the existing logstash format and MDC (`correlationId`, see `observability/CorrelationIdFilter.java:20`).
- Package for new code: `com.expenseanalyzer.observability` (where CorrelationIdFilter lives). One class only.
- Redaction replacement string is exactly `[REDACTED]`.
- Redacted patterns (case-insensitive), applied to the formatted `message` and `stack_trace` JSON members:
  - `(password|passwd|pwd|secret|token|authorization|api[_-]?key|refresh[_-]?token|access[_-]?token)\s*[=:]\s*\S+` -> keep key, value becomes `[REDACTED]`
  - `Bearer\s+[A-Za-z0-9._~+/=-]+` -> `Bearer [REDACTED]`
  - JWT-shaped `eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*` -> `[REDACTED]`
  - `(description|statement)\s*[=:]\s*.+` (to end of message) -> key kept, rest `[REDACTED]` (transaction descriptions / statement contents)
- Do not edit Flyway migrations (no DB change; next would be V9 and is not needed).
- Tests use `@MockitoBean` if mocks needed (none expected). Never log PII/amounts/descriptions in new code.

## Acceptance criteria -> test mapping
| Criterion | Evidence / Test |
|---|---|
| Passwords, tokens, secrets never in logs | Task 1 (`SensitiveLogRedactorTest`), Task 2 (`AuthService` leak removed + `LogRedactionIntegrationTest`) |
| Raw transaction descriptions not logged | No code logs them today (grep of `logger.*` in `backend/src/main/java` shows no transaction/statement logging). Guarded by Task 1 `redactsDescriptionAndStatementContent` |
| Complete statements not logged | Same as above; same test |
| Log sanitisation filters configured | Task 2 (`logging.structured.json.customizer` in both yml files) |
| Automated tests verify redaction | Tasks 1 and 2 |

Existing-code finding: `auth/service/AuthService.java:58` logs `request.password()` raw (`"Request password: {}"`). Emails are also logged at `AuthService.java:48,52,70` and `AuthController.java:89,95` (PII, but outside this issue's criteria; mention in PR, do not change).

### Task 1: Redacting JSON members customizer
Files:
- Create `backend/src/main/java/com/expenseanalyzer/observability/SensitiveLogRedactor.java`
- Create `backend/src/test/java/com/expenseanalyzer/observability/SensitiveLogRedactorTest.java`

Behaviour: `public class SensitiveLogRedactor implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent>` (`org.springframework.boot.logging.structured`) with a public static `String redact(String)` holding the patterns above (compiled `Pattern` constants, null-safe) and `customize(JsonWriter.Members<ILoggingEvent> members)` that applies it to string values of members `message` and `stack_trace` via `members.applyingValueProcessor(ValueProcessor.of(String.class, SensitiveLogRedactor::redact).whenHasPath(p -> "message".equals(p.name()) || "stack_trace".equals(p.name())))` (confirm exact `JsonWriter.ValueProcessor`/path API against Boot 4.1.1 sources; if path filtering is unavailable, apply the redactor to all String values, which is acceptable because the patterns are specific). Needs a public no-arg constructor.

Tests (plain JUnit + AssertJ, in `SensitiveLogRedactorTest`; each fails without the class):
- `redactsPasswordAssignment`: `redact("Request password: hunter2")` does not contain `hunter2`, contains `[REDACTED]`.
- `redactsBearerAndJwt`: `redact("Authorization: Bearer abc.def.ghi")` and a `eyJhbGciOi.eyJzdWIi.sig` string contain no original token.
- `redactsSecretsAndApiKeys`: `secret=abc`, `api_key: xyz`, `refresh_token=r1` all masked.
- `redactsDescriptionAndStatementContent`: `redact("description=COFFEE SHOP 4.50")` and `statement: 2024-01-01,Rent,-1200` lose their values.
- `leavesBenignMessagesUntouched`: `redact("Registration failed: Email already registered")` equals input; `redact(null)` returns null.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.observability.SensitiveLogRedactorTest"`

### Task 2: Wire it up, remove the raw-password log, integration test
Files:
- Modify `backend/src/main/resources/application.yml` (add under existing `logging.structured`): `json: customizer: com.expenseanalyzer.observability.SensitiveLogRedactor`
- Modify `backend/src/test/resources/application.yml` (same addition under its `logging.structured`)
- Modify `backend/src/main/java/com/expenseanalyzer/auth/service/AuthService.java:58`: change to `logger.warn("Registration failed: Password is required");` (drop the value argument).
- Create `backend/src/test/java/com/expenseanalyzer/observability/LogRedactionIntegrationTest.java`

Behaviour: all console JSON log lines pass through the redactor; AuthService no longer emits the password at the source.

Tests (`@SpringBootTest` + `@ExtendWith(OutputCaptureExtension.class)`, `CapturedOutput output`; copy annotations/imports of an existing `@SpringBootTest` in `src/test/java/com/expenseanalyzer/auth/` e.g. `AuthRefreshSecurityTest`):
- `loggedSecretsAreRedactedInJsonOutput`: `LoggerFactory.getLogger("test").warn("login password=hunter2 token=abc123")`; assert `output` contains `[REDACTED]` and not `hunter2` / `abc123`. Fails without the yml wiring.
- `bearerTokenInThrowableIsRedacted`: log an exception whose message is `Bearer abc.def.ghi`; assert stack trace output lacks `abc.def.ghi`.
- `registrationWithBlankPasswordDoesNotLogPassword` (call `AuthService.register` with a request whose password is null/blank per the existing check at `AuthService.java:56-58`, mirroring `AuthServiceTest` setup; if the record cannot carry a distinguishing value there, use a second request with password `"short"` hitting line 64 path and assert `short` plaintext sentinel e.g. `Sh0rt!` not in output): assert output lacks the sentinel password.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.observability.LogRedactionIntegrationTest"` then `./gradlew.bat clean build` before finishing.

## Controller ruling (applies to Task 2)

Ruling: email addresses logged at `AuthService.java:48,52,70` and `AuthController.java:89,95` are IN scope for Task 2 — repo rules forbid logging PII. Remove the email from those log calls (keep the log event, drop the address). Add an assertion to `LogRedactionIntegrationTest` that a login/register log line does not contain the test email.

## Execution record (from SDD ledger)

Spec: GitHub issue #122 acceptance criteria (no separate spec doc).
Preflight scan:
| Pair/Task | Produces/consumes | Finding |
|---|---|---|
| T1->T2 | T1 creates SensitiveLogRedactor customizer; T2 registers it via logging.structured.json.customizer | consistent |
| T1 self | tests vs code consistent | ok |
| T2 self | wiring + AuthService:58 fix + integration test | ok |
Ruling: email logging in AuthService/AuthController brought into Task 2 scope — repo rule forbids PII in logs — cost if wrong: revert a few log lines.
Task 1: dispatched (BASE d0dad9c)
Task 1: minor (deferred): SensitiveLogRedactor.java:42 comment wrong; "Authorization: Bearer" renders "[REDACTED] [REDACTED]"
Task 1: minor (deferred): Bearer rule over-redacts prose ("bearer of"); description rule eats rest of line
Task 1: minor (deferred): test assertions on short strings only doesNotContain; add contains("[REDACTED]")
Task 1: cannot-verify item (customizer path match + MDC untouched) — Ruling: carried into Task 2 as a required integration assertion — cost if wrong: unwired redaction ships
Task 1: complete (commits d0dad9c..3986ed5, review clean)
Task 2: dispatched (BASE 3986ed5)
Task 2: note: implementer reports real register() against H2 throws SQLGrammarException (pre-existing, out of scope) — surface to user
Task 2: minor (deferred): JwtTokenProvider.java:29 logs authentication.getName() (likely email) — PII, outside ruling's list
Task 2: minor (deferred): AuthController.java:95 logs exception object (message could embed email); correlation assertion is substring; debug-line email absence not proven
Task 2: complete (commits 3986ed5..d2c825e, review clean)
Final review: 2 Important (UserService.java:57 logs email; JwtTokenProvider.java:29 logs getName()=email) — final fix wave dispatched (FIX_BASE d2c825e)
Final review: minor (deferred): AuthService.java:64 logs password length
Final fix wave: 2 addressed, 0 open (commits d2c825e..9503128); re-review clean
Final review: parked — UserService.java:63 logs request.fullName(), :83 logs request.preferences() at DEBUG — Ruling: residual PII outside reviewed scope; no second fix wave; surfaced to user as follow-up — cost if wrong: names/preferences appear in DEBUG logs until fixed
