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

### POST `/statements`
Implemented as `POST /api/statements`, `multipart/form-data` with one part named `file`.

- **Formats:** `.csv` (UTF-8 text) and `.xlsx`. Extension, MIME type and leading bytes must agree. PDF is rejected until a PDF parser exists.
- **Limits:** `app.statement.upload.max-size-bytes` (default 20 MB).
- **Idempotency:** files are de-duplicated by SHA-256 per user. Re-uploading an identical file returns `409` with the existing `statementId`, so retries are safe.
- **Account selection:** not yet supported (no accounts module).

Response `201`:
```json
{ "id": "uuid", "fileName": "statement.csv", "format": "CSV", "status": "QUEUED",
  "processingError": null, "createdAt": "...", "completedAt": null }
```

Errors (RFC 7807 `ProblemDetail`):

| Status | When | Extra fields |
|---|---|---|
| 400 | empty, wrong extension/MIME/signature, unsupported format | `reason` |
| 401 | missing/invalid bearer token | |
| 409 | identical file already uploaded by this user | `statementId` |
| 413 | file too large | `reason=FILE_TOO_LARGE` |
| 503 | object storage unavailable (nothing is persisted) | |

### GET `/statements`
List owned statements, newest first, in the pagination envelope (`page`, `size` up to 100).

### GET `/statements/{statementId}`
Get metadata/status. Another user's statement returns `404`.

### GET `/statements/{statementId}/status`
`{ "id", "status", "processingError" }`. `processingError` is a safe code, never file content.

### DELETE `/statements/{statementId}`
Deletes the statement, its transactions and the stored file. `204`, or `404` if not owned.

## 5. Transactions

### GET `/transactions`
Implemented as `GET /api/transactions`. Query parameters (all optional):
`statementId, from, to` (ISO dates, inclusive), `merchantId, direction` (`DEBIT`|`CREDIT`), `minAmount, maxAmount` (currency units, inclusive), `page, size` (size up to 200).
Not yet supported: `accountId`, `categoryId` (no accounts or categories module), custom `sort` (always newest first).

Item:
```json
{ "id": "uuid", "date": "2026-01-02", "description": "UPI-SWIGGY-ORDER", "normalizedDescription": "SWIGGY ORDER",
  "amount": 450.00, "currency": "INR", "direction": "DEBIT", "merchantId": "uuid|null", "statementId": "uuid|null" }
```
`amount` is always positive; `direction` gives the sign. `400` if `from` is after `to` or a parameter is malformed.

### GET `/transactions/{transactionId}`
Get one transaction after ownership validation. Another user's transaction returns `404`.

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
