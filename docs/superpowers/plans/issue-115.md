# Authorization Integration Tests (SEC-004)

Issue: #115

## Goal
Add a MockMvc authorization matrix over every HTTP endpoint that exists today (`/api/auth/*`, `PUT /api/user/{userId}`, `GET /api/user/me`), complementing `UserEndpointSecurityTest`. Test-only: tracing found no authorization bug.

## Findings (current behaviour, traced SecurityConfiguration -> UserController -> UserService)
- Filter chain: `/api/auth/**` and actuator health/prometheus public; everything else `authenticated()`; bearer JWT, stateless; CSRF ignored for `/api/**`. Unauthenticated -> 401.
- `GET /api/user/me`: returns the caller by JWT `sub` (email); no user id in the URL, so no cross-user read path exists.
- `PUT /api/user/{userId}`: unknown id -> 404 (IllegalArgumentException); target email != caller -> `AccessDeniedException` -> 403; any non-null `role` or `active` in body -> 403 even for self. No admin override: an ADMIN updating someone else also gets 403.
- Tokens carry no role claim; roles are never read from the JWT. No admin-only endpoints exist (`UserRole.ADMIN` is unused by any rule, no `/api/admin/**`).
- No bug found. Observation (not fixed, out of scope): unknown id gives 404 while another user's id gives 403, which lets a caller probe id existence.
- Already covered by `UserEndpointSecurityTest` (do not duplicate): me ok / 404 unknown / missing+invalid+expired+wrong-signature token, `/api/users/anything` 401, update own 200, update other 403, self role escalation 403, POST csrf check.

## Global Constraints
- Java 21, Spring Boot 4, Gradle (`./gradlew.bat` from `backend/`), H2 tests, no Docker.
- Copy the `UserEndpointSecurityTest` setup: `@SpringBootTest`, `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())`, real tokens from `JwtTokenProvider.generateToken`, `@MockitoBean UserRepository` and `PreferenceRepository`. Do not use `@WithMockUser`/`TestRestTemplate` (they bypass the real JWT path).
- Every negative test needs a positive control under the same setup in the same test (same request shape, different principal/target) so failures are not due to CSRF, content type or a bad body.
- No production code changes. Never touch `.env`. Do not log/assert on PII beyond test fixtures.

## Acceptance criteria -> tests
| Criterion | Test(s) in `AuthorizationMatrixTest` |
|---|---|
| USER own data -> success | `userReadsAndUpdatesOnlyOwnData` |
| USER other user's data -> 403/404 | `userCannotUpdateAnotherUser`, `meNeverReturnsAnotherUsersData`, `adminRoleInDbDoesNotGrantUpdateOfOthers`, `unknownUserIdIs404` |
| USER own role/active escalation | `userCannotChangeOwnRoleOrActive` |
| USER -> admin endpoints -> 403 | Out of scope: no admin endpoints exist. `nonexistentAdminPathIsNotPublic` only proves fail-closed (401 anon, 404 for USER; not 403) |
| Unauthenticated -> 401 | `unauthenticatedRequestsAreRejected`, `authPathsStayPublic` |
| All resource types | Only `user` and `auth` resources have endpoints. Statements, transactions, accounts etc. have no controllers: out of scope until they exist |

## Tasks

### Task 1: Add AuthorizationMatrixTest
File: `backend/src/test/java/com/expenseanalyzer/security/AuthorizationMatrixTest.java` (new; package `com.expenseanalyzer.security`).

Setup: alice (USER) and bob (USER) and carol (role ADMIN), each with random UUID id, email, stubbed on `findByEmail` and `findById`; `save` returns its argument. `bearer(email)` helper as in the existing test.

Tests (name -> request -> expected):
1. `userReadsAndUpdatesOnlyOwnData`: GET `/api/user/me` as alice -> 200 `$.email=alice`; PUT `/api/user/{alice.id}` as alice `{"fullName":"Alice B"}` -> 200.
2. `meNeverReturnsAnotherUsersData`: GET `/api/user/me` as bob -> 200 and `$.email=bob` (not alice); positive: as alice -> `$.email=alice`.
3. `userCannotUpdateAnotherUser`: PUT `/api/user/{bob.id}` as alice `{"fullName":"X"}` -> 403, `verify(userRepository, never()).save(any())`; positive control: PUT `/api/user/{bob.id}` as bob same body -> 200. Also reverse (bob -> alice.id) -> 403.
4. `adminRoleInDbDoesNotGrantUpdateOfOthers`: PUT `/api/user/{alice.id}` as carol (ADMIN) `{"fullName":"X"}` -> 403 (documents current no-override behaviour); positive: carol PUT `/api/user/{carol.id}` -> 200.
5. `userCannotChangeOwnRoleOrActive`: PUT own id `{"role":"ADMIN"}` -> 403, `{"role":"USER"}` -> 403, `{"active":false}` -> 403, `{"active":true}` -> 403, each with `never().save`; positive: `{"fullName":"Ok"}` -> 200 on same id/token.
6. `unknownUserIdIs404`: PUT `/api/user/{random uuid}` as alice `{"fullName":"X"}` -> 404; positive: own id -> 200.
7. `unauthenticatedRequestsAreRejected`: PUT `/api/user/{alice.id}` and GET `/api/user/me` with no header -> 401; with `Authorization: Bearer <alice token>` same requests -> 200. Include a `Basic` header (`Authorization: Basic ...`) on `/me` -> 401.
8. `authPathsStayPublic`: GET `/api/auth/register/check-email?email=new@example.com` with no token -> not 401/403 (expect 200 with `existsByEmail` default false via mock; assert `status().isOk()`); POST `/api/auth/refresh` without cookie -> 401 from handler body (existing AuthRefreshSecurityTest covers deeper cases; assert only that response is 401 *and* a non-auth path e.g. GET `/api/user/me` is 401, plus `check-email` 200 as the positive).
9. `nonexistentAdminPathIsNotPublic`: GET `/api/admin/users` no token -> 401; as alice -> 404; (documents fail-closed, no admin endpoints; add a TODO comment to convert to 403 when admin endpoints and role rules land).

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.security.AuthorizationMatrixTest"`; then `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.security.UserEndpointSecurityTest"` and `cd backend && ./gradlew.bat clean build` before finishing.
Note: check-email may need `UserAuthRepository`/`existsByEmail` stubbing depending on `AuthService`; if the mocked `UserRepository` default (false) is insufficient, stub it explicitly.
