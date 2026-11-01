# Issue #332 [MOB-002] Refresh token over non-cookie transport

Spec: GitHub issue #332, part of epic #330. No other spec exists.

## Global Constraints

- Opt-in header `X-Client-Type: mobile` (exact, case-insensitive value) selects body transport. Any other value or no header means the cookie flow.
- Web cookie flow unchanged: same cookie attributes, and `refreshToken` absent from the JSON response. Existing `AuthRefreshSecurityTest` passes with no edits.
- Transports do not mix. Mobile requests read only the body and never receive `Set-Cookie`. Cookie requests read only the cookie and ignore any body.
- Rotation and revocation go through the existing `RefreshTokenService`, unchanged.
- Refresh token never logged. No new log statements carrying it.
- No Flyway migration, no new packages, no rate limiter, no CORS change.

## Task 1: Body transport for login, refresh, logout

Files: `auth/dto/AuthResponse.java`, new `auth/dto/RefreshRequest.java`, `auth/controller/AuthController.java`, new test `src/test/java/com/expenseanalyzer/security/AuthMobileRefreshSecurityTest.java`.

1. `AuthResponse(String token, String refreshToken)` with `@JsonInclude(NON_NULL)`, and a one-argument constructor `AuthResponse(String token)` for the web path.
2. `RefreshRequest(String refreshToken)` record. Its `toString()` must not expose the value.
3. `POST /api/auth/login` with the header: `200`, body has `token` and `refreshToken`, no `Set-Cookie`.
4. `POST /api/auth/refresh` with the header: reads `refreshToken` from an optional JSON body. Missing body, null or blank is `401`. Success rotates and returns both tokens. Failure is `401` with no `Set-Cookie`. A cookie sent alongside is ignored.
5. `POST /api/auth/logout` with the header: revokes the body token if present, always `204`, no `Set-Cookie`.
6. Without the header, a `refreshToken` in the body is ignored on refresh and logout.

Tests (same setup as `AuthRefreshSecurityTest`):
- mobile login returns both tokens and no cookie
- mobile refresh returns a working access token and a new refresh token
- reuse of a rotated token over mobile is 401
- missing, blank, unknown and expired tokens over mobile are 401 with no cookie
- mobile logout revokes, and a later refresh is 401
- a valid cookie with the mobile header and no body is 401, and the token still works afterwards
- a valid body token without the header is 401, and the token still works afterwards
- web login response JSON has no `refreshToken`
- a token issued over one transport works over the other once (same store, same rotation)

Verify: `./gradlew.bat test --tests "*AuthMobileRefreshSecurityTest" --tests "*AuthRefreshSecurityTest"`, then `./gradlew.bat clean build` (from `backend/`).
