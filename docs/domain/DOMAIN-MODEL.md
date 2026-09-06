# Domain Model

**Document ID:** ARCH-002  
**Version:** 0.1

## 1. Entity Overview

```text
User
 ├── Account
 │    └── Transaction
 ├── Statement
 ├── RecurringExpense
 ├── Insight
 ├── AIQuery
 └── AuditLog

Transaction --> Merchant
Transaction --> Category
RecurringExpense --> Merchant
RecurringExpense --> Category
Category --> Category (parent)
Statement --> Account
Statement --> User
```

## 2. Entities

### User
`id, email, name, status, created_at, updated_at`

### Account
`id, user_id, type, institution_name, masked_identifier, currency, status, created_at`

### Statement
`id, user_id, account_id, file_name, storage_key, file_hash, format, period_start, period_end, status, processing_error, created_at, completed_at`

### Transaction
`id, user_id, account_id, statement_id, transaction_date, posting_date, amount, currency, direction, raw_description, normalized_description, merchant_id, category_id, category_confidence, merchant_confidence, transaction_fingerprint, created_at, updated_at`

### Merchant
`id, canonical_name, parent_merchant_id, category_hint, created_at, updated_at`

### Category
`id, parent_id, name, type, is_system_defined`

### RecurringExpense
`id, user_id, merchant_id, category_id, expected_amount, frequency, confidence, last_occurrence, next_expected_date, status`

### Insight
`id, user_id, type, title, description, severity, period_start, period_end, evidence, created_at`

### AIQuery
`id, user_id, question, intent, tool_calls, result_reference, response, created_at`

### AuditLog
`id, user_id, action, resource_type, resource_id, timestamp, request_id, metadata`

## 3. Invariants
- Every protected financial entity belongs to a user directly or through an owned parent.
- Transaction amounts and calculations use precise decimal arithmetic.
- Raw descriptions are preserved.
- A transaction must not reference a category or merchant belonging to another tenant/context.
- Fingerprints are stable enough for duplicate detection.
- Insight evidence must correspond to data available for the referenced period.

## 4. Financial Semantics
The model must eventually make explicit decisions for:
- transfers between own accounts
- credit-card payments
- refunds/reversals
- income vs expense direction
- investment contributions
- cash withdrawals
- EMI principal/interest representation

These are product/domain decisions that should be resolved before relying on analytics for financial advice.

## 5. Persistence Guidance
Use foreign keys and indexes for user/account/date/category/merchant queries. Apply uniqueness constraints where appropriate for account ownership, statement checksum scope, and idempotency keys.
