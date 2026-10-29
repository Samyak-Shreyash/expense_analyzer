# Security Requirements: Access Control and Audit

Covers what is implemented. Statement storage and parser limits are documented in
`docs/architecture/LLD.md` sections 21.1 and 8.4.

## 1. Resource ownership rules

| Layer | Rule |
|---|---|
| API | The caller is always the JWT `sub` (`@AuthenticationPrincipal Jwt`). Controllers never accept a user id from the client (one pre-existing exception: `PUT /api/user/{userId}`, checked against the token's user in `UserService`). |
| API | A resource owned by someone else answers exactly like a missing one: `404` with the same body. Never `403`, so ids can't be probed. |
| Service | Every lookup of a user-owned resource includes the owner: `findByIdAndUserId`, `findByUserId...`, and the `userId` predicate in transaction filters and analytics aggregations. Services resolve the user from the email, so calling a service directly with another user's resource id finds nothing. |
| Database | `statements.user_id` and `transactions.user_id` are NOT NULL with FKs to `users`. Uniqueness is per user: `(user_id, file_hash)`, `(user_id, transaction_fingerprint)`. |
| Worker | A `StatementUploaded` event is processed only if the event's `userId` owns the statement. |
| Shared data | `merchants` rows are global and contain no user data; user-specific amounts are only reachable through owned transactions. |

Owned resources today: statements (and stored files), transactions, analytics derived from them.
Not built yet: accounts, insights, AI queries. Each must follow the same rules when added.

## 2. Authorization matrix

Roles: `USER`, `ADMIN`. No admin-only endpoints exist yet; ADMIN has no extra data access.

| Endpoint | Anonymous | USER (own data) | USER (other's data) |
|---|---|---|---|
| `POST /api/auth/login`, `/register`, `/refresh`, `/logout` | allow | allow | n/a |
| `GET /actuator/health/**`, `/actuator/prometheus` | allow | allow | n/a |
| `POST /api/statements` | 401 | 201 | n/a |
| `GET /api/statements`, `/api/transactions`, `/api/analytics/*` | 401 | 200, own rows only | never included |
| `GET /api/statements/{id}`, `/{id}/status`, `/api/transactions/{id}` | 401 | 200 | 404 |
| `DELETE /api/statements/{id}` | 401 | 204 | 404, nothing deleted |
| Any audit log endpoint | none exists | none exists | none exists |

## 3. Cross-user isolation test matrix

Implemented in `CrossUserIsolationTest`, `StatementControllerTest`, `TransactionControllerTest`,
`AnalyticsControllerTest`, `StatementProcessorTest`:

| Scenario | Covered by |
|---|---|
| Guessed / foreign id on read, status, delete | `CrossUserIsolationTest`, controller tests |
| Foreign id indistinguishable from random id | `CrossUserIsolationTest.foreignIdIsIndistinguishableFromAGuessedOne` |
| Foreign `statementId` / `merchantId` filter | `CrossUserIsolationTest.filteringByForeignStatementOrMerchantReturnsNothing` |
| Injected `userId` query parameter | `CrossUserIsolationTest.injectedUserIdParameterIsIgnored` |
| Analytics never mix users | `CrossUserIsolationTest`, `AnalyticsControllerTest` |
| Direct service calls | `CrossUserIsolationTest.servicesScopeEveryLookupToTheCaller` |
| Same file / same transactions for two users | `StatementControllerTest`, `StatementProcessorTest` |
| Forged event user | `StatementProcessorTest.eventWhoseUserDoesNotOwnStatementIsIgnored` |
| Accounts, insights, AI | to add with those modules |

**Regression guard:** `ControllerAuthorizationRulesTest` scans every `@RestController` and fails the
build if a non-public controller lacks class-level `@PreAuthorize`, a handler lacks an
`@AuthenticationPrincipal Jwt` parameter, a handler accepts `userId` from the client, or a mapping
exposes audit logs.

## 4. Audit event model

Table `audit_logs` (V13), written by `AuditService`, append-only (`@Immutable` entity, no update path).

| Field | Content |
|---|---|
| `user_id` | acting user; NULL when unknown (failed login for an unregistered email) |
| `action` | `AuditAction` enum code |
| `resource_type`, `resource_id` | e.g. `STATEMENT` + statement id; NULL for auth events |
| `correlation_id` | request's `X-Correlation-ID` |
| `occurred_at` | server time |

**Audited actions:** `AUTH_REGISTERED`, `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`, `STATEMENT_UPLOADED`,
`STATEMENT_ACCESSED` (detail/status view of an owned statement), `STATEMENT_DELETED`. Transaction edits,
AI actions and admin actions are added when those features exist.

**Sensitive-data exclusions:** no emails, passwords, tokens, file names, file hashes, amounts,
descriptions or free-text metadata. There is no metadata column on purpose.

**Transactions:** records join the business transaction, so a rolled-back upload leaves no record.
Login events are written on their own.

**Access:** no API exposes audit records; they are read with database access only.

**Retention:** not enforced yet. Proposed: 1 year, purged by a scheduled job (to be decided).
