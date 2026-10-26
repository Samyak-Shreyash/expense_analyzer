# SEC-012 Secure Secrets Management Implementation Plan

Issue: #123

Goal: No secret values (including fallback defaults) in `backend/src/main/resources/application.yml` or Java source; secrets come only from environment variables and the app fails fast at startup if one is missing.

## Global Constraints
- Edit only `backend/src/` (plus nothing else; compose already injects `DB_USER_PASSWORD`, `JWT_SECRET` via env, compose.yaml:18,88). Gradle, not Maven. Use `@MockitoBean` if mocks are needed.
- Decision: environment variables only. NO Spring Cloud AWS / AWS Secrets Manager dependency (issue allows env vars; no AWS infra exists in repo). No S3/AI-key code exists, so nothing to secure for them yet.
- Rotation: out of scope as code. Env-based secrets are rotated by changing the env value and restarting. Zero-downtime JWT key rotation (kid/multi-key) is NOT implemented.
- Do not touch `src/main/resources/application-test.yml` or `src/test/resources/application.yml` (test-only dummy values, tests need them). Never read/edit `.env`.
- Exact property keys: `spring.datasource.password` = `${DB_USER_PASSWORD}` (no default); `app.oauth.jwt.secret` stays `${JWT_SECRET}`; remove the `:default-secret-key-for-testing-only-32-bytes` fallback in `@Value` at `auth/dto/JwtTokenProvider.java:22` and `auth/config/SecurityConfiguration.java:106`.
- Non-secret defaults (`DB_USER:app_user`, ports, flags) stay.
- Existing tests must still pass: test profile supplies `app.oauth.jwt.secret` so removing the Java default is safe.

## Acceptance criteria mapping
| Criterion | Status |
|---|---|
| No secrets in application.yml or source | Task 1 (password default `application.yml:11`; JWT fallbacks in Java) + guard test |
| Injected from env vars | Already satisfied: `application.yml` `${JWT_SECRET}`, `${OAUTH_SECRET}`; compose.yaml:18,88 inject DB creds; Task 1 removes remaining defaults |
| DB creds, JWT keys, S3, AI keys stored securely | DB + JWT: Task 1/2. S3/AI keys: out of scope (no such config exists in the repo) |
| Secrets rotation supported | Out of scope beyond restart-with-new-env (see constraints) |

### Task 1: Remove hard-coded secret defaults
Files: `backend/src/main/resources/application.yml`, `backend/src/main/java/com/expenseanalyzer/auth/dto/JwtTokenProvider.java`, `backend/src/main/java/com/expenseanalyzer/auth/config/SecurityConfiguration.java`.
Behaviour:
- `application.yml:11` -> `password: ${DB_USER_PASSWORD}`.
- `JwtTokenProvider` `@Value("${app.oauth.jwt.secret}")`; `SecurityConfiguration.jwtDecoder` `@Value("${app.oauth.jwt.secret}")`.
Test: new `backend/src/test/java/com/expenseanalyzer/security/NoHardcodedSecretsTest.java` (plain JUnit, no Spring):
1. reads `src/main/resources/application.yml` as text and asserts no line whose key matches `(?i)(password|secret|key|token)\s*:` has a value containing a `:default` fallback in `${NAME:value}` form with non-empty value (fails today on `app_password`).
2. reads `JwtTokenProvider.java` and `SecurityConfiguration.java` under `src/main/java` and asserts they do not contain `default-secret-key` (fails today).
Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.security.NoHardcodedSecretsTest"`

### Task 2: Fail fast when a required secret is missing
Files: `backend/src/test/java/com/expenseanalyzer/security/MissingSecretStartupTest.java` (test only; behaviour comes from Task 1).
Test (`ApplicationContextRunner`, no full app): register `JwtTokenProvider` (and `PropertySourcesPlaceholderConfigurer` via `withUserConfiguration`/`withBean` as needed):
1. no `app.oauth.jwt.*` properties -> `context.hasFailed()` and root cause mentions `app.oauth.jwt.secret` (fails today because default silently starts).
2. with `app.oauth.jwt.secret=<32+ char dummy>` and `app.oauth.jwt.tokenExpirationSeconds=3600` -> context starts.
Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.security.MissingSecretStartupTest"`

Finally run `cd backend && ./gradlew.bat clean build` to confirm all existing tests pass with the test profile.
