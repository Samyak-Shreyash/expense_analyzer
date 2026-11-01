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

Owned resources today: statements (and stored files), transactions, analytics derived from them,
categories, people and their payee names, per-merchant category assignments, merchant names (aliases).
Not built yet: accounts, insights, AI queries. Each must follow the same rules when added.

## 2. Authorization matrix

Roles: `USER`, `ADMIN`. No admin-only endpoints exist yet; ADMIN has no extra data access.

| Endpoint | Anonymous | USER (own data) | USER (other's data) |
|---|---|---|---|
| `POST /api/auth/login`, `/register`, `/refresh`, `/logout` | allow | allow | n/a |
| `GET /actuator/health/**`, `/actuator/prometheus` | allow | allow | n/a |
| `PUT /api/user/me`, `/api/user/me/password`, `DELETE /api/user/me` | 401 | 200, 204, 204 | n/a, caller comes from the token |
| `POST /api/statements` | 401 | 201 | n/a |
| `GET /api/statements`, `/api/transactions`, `/api/analytics/*` | 401 | 200, own rows only | never included |
| `GET /api/statements/{id}`, `/{id}/status`, `/api/transactions/{id}` | 401 | 200 | 404 |
| `DELETE /api/statements/{id}` | 401 | 204 | 404, nothing deleted |
| `PUT /api/transactions/{id}/transfer`, `/reversal`, `/category`, `/counterparty`, `/merchant`; `POST /{id}/suggestion-report` | 401 | 200 (report: 204) | 404, nothing changed; also 404 when a referenced `pairWithId` or `categoryId` is someone else's |
| `PUT`, `DELETE /api/categories/{id}` | 401 | 200, 204 | 404, nothing changed |
| `PUT /api/people/{id}`, `/{id}/category`, `/{id}/payees`; `POST /api/people/{id}/merge` | 401 | 200 | 404, nothing changed; also 404 when a referenced person or category is someone else's |
| `PUT /api/merchants/{id}/category` | 401 | 204 | merchants are shared and the assignment is private to the caller; 404 when `categoryId` is someone else's |
| `PUT /api/merchant-names/{id}`, `/{id}/sharing` | 401 | 200 | 404, nothing changed |
| Any audit log endpoint | none exists | none exists | none exists |

## 3. Cross-user isolation test matrix

Implemented in `CrossUserIsolationTest`, `StatementControllerTest`, `TransactionControllerTest`,
`AnalyticsControllerTest`, `StatementProcessorTest` and the feature tests named below:

| Scenario | Covered by |
|---|---|
| Guessed / foreign id on read, status, delete | `CrossUserIsolationTest`, controller tests |
| Foreign id indistinguishable from random id | `CrossUserIsolationTest.foreignIdIsIndistinguishableFromAGuessedOne` |
| Foreign `statementId` / `merchantId` filter | `CrossUserIsolationTest.filteringByForeignStatementOrMerchantReturnsNothing` |
| Injected `userId` query parameter | `CrossUserIsolationTest.injectedUserIdParameterIsIgnored` |
| Analytics never mix users, on every endpoint | `CrossUserIsolationTest.analyticsNeverIncludeOtherUsersData`, `.remainingAnalyticsNeverIncludeOtherUsersData` |
| Foreign transaction on write, foreign pair or category reference | `CategoryAssignmentTest`, `PeopleTest`, `MerchantAliasTest`, `TransferTest`, `ReversalTest`, `MerchantNameSharingTest` |
| Foreign category on update, delete | `CategoryControllerTest.otherUsersCategoryIsNotFoundAndHidden` |
| Foreign person on update, foreign merge target | `PeopleTest` |
| Foreign person on category, merge, payee move | `CrossUserIsolationTest.foreignPersonCannotBeChanged` |
| Own person given a foreign category or moved to a foreign person | `CrossUserIsolationTest.ownPersonCannotAdoptForeignCategoryOrMoveToForeignPerson` |
| Foreign merchant name on rename, sharing | `MerchantNameSharingTest` |
| ADMIN has no access to other users' financial data | `CrossUserIsolationTest.adminHasNoAccessToOtherUsersFinancialData`, `AuthorizationMatrixTest.adminRoleInDbDoesNotGrantUpdateOfOthers` |
| Direct service calls | `CrossUserIsolationTest.servicesScopeEveryLookupToTheCaller` |
| Same file / same transactions for two users | `StatementControllerTest`, `StatementProcessorTest` |
| Forged event user | `StatementProcessorTest.eventWhoseUserDoesNotOwnStatementIsIgnored` |
| Accounts, insights, AI | to add with those modules |

**Regression guard:** `ControllerAuthorizationRulesTest` scans every `@RestController` and fails the
build if a non-public controller lacks class-level `@PreAuthorize`, a handler lacks an
`@AuthenticationPrincipal Jwt` parameter, a handler accepts `userId` from the client, or a mapping
exposes audit logs.

## 4. Audit event model

Table `audit_logs` (V11), written by `AuditService`, append-only (`@Immutable` entity). The one update is account deletion, which sets `user_id` to null.

| Field | Content |
|---|---|
| `user_id` | acting user; NULL when unknown (failed login for an unregistered email) |
| `action` | `AuditAction` enum code |
| `resource_type`, `resource_id` | e.g. `STATEMENT` + statement id; NULL for auth events |
| `correlation_id` | request's `X-Correlation-ID` |
| `occurred_at` | server time |

**Audited actions:** `AUTH_REGISTERED`, `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`, `AUTH_PASSWORD_CHANGED`, `STATEMENT_UPLOADED`,
`STATEMENT_ACCESSED` (detail/status view of an owned statement), `STATEMENT_DELETED`, `ACCOUNT_DELETED` (recorded with no user id). Transaction edits,
AI actions and admin actions are added when those features exist.

**Sensitive-data exclusions:** no emails, passwords, tokens, file names, file hashes, amounts,
descriptions or free-text metadata. There is no metadata column on purpose.

**Transactions:** records join the business transaction, so a rolled-back upload leaves no record.
Login events are written on their own.

**Access:** no API exposes audit records; they are read with database access only.

**Retention:** not enforced yet. Proposed: 1 year, purged by a scheduled job (to be decided).
