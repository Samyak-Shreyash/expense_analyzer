# Domain Model

**Document ID:** ARCH-002  
**Version:** 0.2

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


## 6. Database Migration Baseline

The database migration baseline implements the approved domain model in dependency order. It establishes persistence structure, relationships, indexes, and confirmed invariants without introducing unresolved financial semantics.

### 6.1 Migration Ordering

1. `users`
2. `accounts`
3. `merchants`
4. `categories`
5. `statements`
6. `transactions`
7. `recurring_expenses`
8. `insights`
9. `ai_queries`
10. `audit_logs`

### 6.2 Table-to-Domain Mapping

| Domain entity | Database table | Primary key | Required relationships |
|---|---|---|---|
| User | `users` | `id` | Root ownership entity |
| Account | `accounts` | `id` | `user_id → users.id` |
| Statement | `statements` | `id` | `user_id → users.id`; `account_id → accounts.id` |
| Transaction | `transactions` | `id` | `user_id → users.id`; `account_id → accounts.id`; `statement_id → statements.id`; `merchant_id → merchants.id`; `category_id → categories.id` |
| Merchant | `merchants` | `id` | `parent_merchant_id → merchants.id` |
| Category | `categories` | `id` | `parent_id → categories.id` |
| RecurringExpense | `recurring_expenses` | `id` | `user_id → users.id`; `merchant_id → merchants.id`; `category_id → categories.id` |
| Insight | `insights` | `id` | `user_id → users.id` |
| AIQuery | `ai_queries` | `id` | `user_id → users.id` |
| AuditLog | `audit_logs` | `id` | `user_id → users.id` |

### 6.3 Constraints

The baseline enforces the confirmed model invariants: primary keys; unique user email; ownership foreign keys; transaction/account/statement/merchant/category relationships; self-referencing merchant/category relationships; exact decimal/numeric transaction amounts; preservation of raw descriptions; statement checksum uniqueness scoped appropriately; and transaction fingerprints for duplicate detection/idempotency. Finite status/type/frequency domains are constrained where the approved model defines them.

### 6.4 Index Baseline

The baseline indexes user/account/date/category/merchant query paths, including `accounts(user_id)`, `statements(user_id)`, `statements(account_id)`, scoped statement checksum uniqueness, transaction indexes on user/date, account/date, statement, category/date, merchant/date, and fingerprint, plus recurring-expense user/date, insight user/period, AI-query user/created-at, and audit-log user/timestamp and request-id indexes.

### 6.5 Ownership and Tenant-Safety Boundary

The approved model requires transactions not to reference merchants or categories from another tenant/context, but Merchant and Category currently have no `user_id`. Ordinary foreign keys therefore cannot independently enforce that invariant. The baseline must not silently change the domain model by adding tenant ownership columns. The ownership-enforcement design remains a follow-up schema/domain decision.

### 6.6 Migration and Schema Tests

Migration validation covers clean creation from an empty PostgreSQL database, migration ordering, primary/foreign-key enforcement, unique email, statement checksum duplicate detection, transaction fingerprint duplicate/idempotency behavior, exact decimal persistence, self-referencing merchant/category relationships, baseline indexes, and rejection of references to missing parent records.

### 6.7 Documentation Synchronization

`DOMAIN-MODEL.md` is the document of record. Changes to entities, relationships, invariants, indexes, or constraints represented here must update this document in the same change. The baseline remains within scope: it maps the approved model to persistence and does not introduce new product semantics.

### 6.8 Explicitly Deferred Financial Semantics

The baseline does not encode unresolved rules for transfers between own accounts, credit-card payments, refunds/reversals, income versus expense direction, investment contributions, cash withdrawals, or EMI principal/interest representation. These remain product/domain decisions.
