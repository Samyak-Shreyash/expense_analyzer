# API Contract

**Document ID:** API-001  
**Version:** 0.1  
**Base path:** `/api/v1`

## 1. Conventions
- JSON request/response bodies unless upload endpoint.
- ISO-8601 timestamps.
- Stable resource IDs.
- Pagination for list endpoints.
- Authenticated endpoints require a bearer session/token mechanism.
- Error responses use a consistent structure.

Example error:
```json
{
  "code": "STATEMENT_UNSUPPORTED_FORMAT",
  "message": "The uploaded statement format is not supported.",
  "requestId": "req_123"
}
```

## 2. Authentication

### POST `/auth/register`
Create a user.

### POST `/auth/login`
Authenticate a user.

### POST `/auth/logout`
Invalidate the current session.

### GET `/users/me`
Return the authenticated user.

## 3. Accounts

### POST `/accounts`
Create an account.

Request:
```json
{
  "type": "BANK",
  "institutionName": "Example Bank",
  "maskedIdentifier": "XXXX1234",
  "currency": "INR"
}
```

### GET `/accounts`
List owned accounts.

### GET `/accounts/{accountId}`
Get an account after ownership validation.

### PATCH `/accounts/{accountId}`
Update allowed account fields.

### DELETE `/accounts/{accountId}`
Delete/archive an account according to retention rules.

## 4. Statements

Statement endpoints are served under `/api/statements` (not `/api/v1`). All require a bearer JWT (401 otherwise). The caller is taken from the token, never from request parameters. Every read and write is scoped to the owner: a statement owned by someone else is reported as 404, never 403.

Status values: `QUEUED`, `PROCESSING`, `PROCESSED`, `FAILED`. An upload persists the statement as `QUEUED`; no broker handoff exists yet (tracked in #189).

Account selection is deferred: upload takes no account field until an Account module exists. Account fields on a statement are filled after parsing.

Errors are Problem Details bodies (`application/problem+json`). Invalid-file errors carry `title: "Invalid file"` and a `reason` property. Other errors added by the upload endpoint carry a `code` property (see below).

### POST `/api/statements`
Multipart upload (`multipart/form-data`) with a single part named `file`. Accepted formats: CSV, XLS, XLSX. Maximum size is 20 MB, set by `app.statement.upload.max-size-bytes` (default `20971520`).

Duplicate detection is idempotent per user on the SHA-256 of the file content.

Response `202 Accepted`:
```json
{
  "id": "stmt_123",
  "status": "QUEUED",
  "fileName": "statement.csv"
}
```

| Status | When | Body |
|---|---|---|
| 202 | Accepted and stored | `{id, status: "QUEUED", fileName}` |
| 400 | Missing, empty, unsupported or mismatched file | `reason`: `EMPTY_FILE`, `UNSUPPORTED_EXTENSION`, `MIME_TYPE_MISMATCH` or `SIGNATURE_MISMATCH` |
| 401 | Missing or invalid token | |
| 409 | Same user already uploaded identical content | `code: "STATEMENT_DUPLICATE"`; reveals only the existing statement `id` |
| 413 | File larger than the maximum size | `reason: "FILE_TOO_LARGE"` |
| 503 | Object storage failure | `code: "STATEMENT_STORAGE_UNAVAILABLE"` |

### GET `/api/statements`
List owned statements, newest first.

### GET `/api/statements/{id}`
Get metadata and status. 404 if not owned.

### GET `/api/statements/{id}/status`
Get processing status and safe error details (an error code only, never file contents). 404 if not owned.

### DELETE `/api/statements/{id}`
Delete a statement: removes the stored object, then the row. Returns 204. 404 if not owned.

## 5. Transactions

### GET `/transactions`
Query parameters:
`accountId, statementId, from, to, categoryId, merchantId, direction, minAmount, maxAmount, page, size, sort`

### GET `/transactions/{transactionId}`
Get one transaction after ownership validation.

### PATCH `/transactions/{transactionId}`
Update user-editable transaction metadata such as category/merchant where supported.

## 6. Analytics

### GET `/analytics/summary`
Period spending/income/cash-flow summary.

### GET `/analytics/categories`
Category totals for a period.

### GET `/analytics/merchants`
Merchant totals for a period.

### GET `/analytics/trends`
Monthly/period trends.

### GET `/analytics/cash-flow`
Income, expense, and net cash-flow summary.

### GET `/analytics/recurring`
Detected recurring expenses.

### GET `/analytics/anomalies`
Detected unusual activity.

## 7. Insights

### GET `/insights`
List generated insights.

### GET `/insights/{insightId}`
Get insight plus evidence metadata.

## 8. AI

### POST `/ai/query`
Request:
```json
{
  "question": "Where did I spend the most last month?"
}
```
The service must translate supported questions into controlled analysis operations. It must not grant arbitrary database access to an LLM.

## 9. Pagination
List endpoints should return a consistent envelope:
```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalItems": 0,
  "totalPages": 0
}
```

## 10. Authorization
Every resource endpoint must enforce ownership. Never rely on the client-provided user ID.

## 11. Idempotency
Upload and mutating operations that can be retried should support idempotency keys where operationally useful.

## 12. API Evolution
Use additive, backward-compatible changes where practical. Version breaking changes under a new API version.
