# Domain Model

**Document ID:** ARCH-002  
**Version:** 1.0

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
Transaction --> Person
Transaction --> Transaction (transfer pair)
Person --> Category (money-out / money-in defaults)
UserMerchantCategory --> Merchant, Category (money-out / money-in defaults)
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

Not yet persisted. Until it is, an account is identified by its statements' `(account_number, bank_name)`; transfer detection (section 4.3) uses that pair.

### Statement
`id, user_id, account_id, file_name, storage_key, file_hash, format, period_start, period_end, status, processing_error, created_at, completed_at`

### Transaction
Target: `id, user_id, account_id, statement_id, transaction_date, posting_date, amount, currency, direction, raw_description, normalized_description, merchant_id, person_id, category_id, transfer_pair_id, category_confidence, merchant_confidence, transaction_fingerprint, created_at, updated_at`

Implemented today (`transactions`): `id, user_id, statement_id, date, amount_cents, currency, type, raw_description, normalized_description, merchant_id, merchant_confidence, person_id, category_id, transfer, transfer_pair_id, transfer_manual, transaction_fingerprint, created_at, updated_at`.

- `amount_cents` holds the amount in minor units (paise), always positive; the API exposes it in currency units. `type` is the direction: `DEBIT` (money out) or `CREDIT` (money in).
- `merchant_id` and `person_id` are mutually exclusive: a counterparty is a merchant or a person, never both.
- `category_id` is the transaction's own category, set only by the user. The effective category is derived (section 4.1); it is not stored.
- `transfer` marks money moved between the user's own accounts; `transfer_pair_id` links the two halves of a pair (null for a single transfer the user marked); `transfer_manual` records that the user marked or unmarked the row, so automatic matching leaves it alone.

### Person
`id, user_id, name, category_id, credit_category_id, created_at`

Someone the user transfers money to or receives it from, detected from UPI P2A/P2P transfers or set by the user. Private to the user. `category_id` / `credit_category_id` are the money-out / money-in default categories. Several payee names can belong to one person (`person_payees`: `user_id, payee_key, person_id`); grouping is always undoable.

### Merchant
`id, canonical_name, parent_merchant_id, category_hint, created_at, updated_at`

### Category
Target: `id, parent_id, name, type, is_system_defined`

Implemented today (`categories`): `id, user_id, name, counts_as_spending, created_at, updated_at`. Categories are per user (seeded with defaults once), unique by name ignoring case. `counts_as_spending = false` keeps a category's money out of spending (e.g. Repayment, Loan).

### UserMerchantCategory
`user_id, merchant_id, category_id, credit_category_id`

The user's money-out / money-in default categories for a shared merchant. Either may be null; a row with neither is removed.

### UserMerchantAlias
`user_id, payee_key, merchant_id, display_name, public_id, share_opt_out, created_at`

The user's own merchant name for a payee key, applied to every payment to that payee.

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
- A transaction has at most one counterparty: a merchant or a person.
- People, user categories, merchant defaults and aliases are visible only to their owner; another user's record behaves as missing (404).
- A transfer pair joins exactly two transactions of the same user: one `DEBIT` and one `CREDIT` of equal amount and currency, in different accounts. A transaction is in at most one pair.

## 4. Financial Semantics

### 4.1 Direction and effective category
- Direction comes from the statement: `DEBIT` is money out, `CREDIT` is money in. Amounts are stored positive.
- Effective category, in order: the transaction's own category; else its person's default for the transaction's direction; else its merchant mapping's default for that direction; else the user's category named like the merchant's `category_hint`; else Uncategorized. A reference to a category that is not the user's counts as not set.
- Spending counts `DEBIT`s whose effective category counts as spending (Uncategorized does). Inflow counts every `CREDIT`.

### 4.2 Refunds and reversals
A `CREDIT` from a matched merchant nets against that merchant's (and its category's) spend. A `CREDIT` with no merchant is treated as income, not a refund.

### 4.3 Transfers between own accounts (#327)
- Automatic pairing: a `DEBIT` in one account and a `CREDIT` of the same amount and currency in a different account of the same user, with transaction dates at most 3 days apart. Each transaction joins at most one pair; when several candidates match, the closest date wins (ties: earliest created).
- A description naming the user, "SELF" or "OWN A/C" may support a match but is never sufficient on its own.
- Manual marking: the user can mark a single transaction as a transfer to or from their own account even when the other half is not imported, and can unmark any pair or single transfer.
- Transfers are excluded from spending, from inflow and from merchant/category breakdowns, and are reported separately. Both halves of a pair are excluded together, so net cash flow is unchanged.
- Marking never alters raw transaction data and is fully reversible.

### 4.4 Still to decide
- credit-card payments
- investment contributions
- cash withdrawals
- EMI principal/interest representation

These are product/domain decisions that should be resolved before relying on analytics for financial advice.

## 5. Persistence Guidance
Use foreign keys and indexes for user/account/date/category/merchant queries. Apply uniqueness constraints where appropriate for account ownership, statement checksum scope, and idempotency keys.


## 6. Database Migration Baseline

The database migration baseline implements the approved domain model in dependency order. It establishes persistence structure, relationships, indexes, and confirmed invariants without introducing unresolved financial semantics.

### 6.1 Migration Ordering

Implemented (one `create_<table>` migration per table, V1-V18): `users`, `statements`, `merchants`, `categories`, `people`, `transactions`, `preferences`, `refresh_tokens`, `outbox_events`, `processed_events`, `audit_logs`, `user_merchant_categories`, `user_merchant_aliases`, `merchant_suggestion_votes`, `merchant_suggestion_reports`, `hidden_merchant_suggestions`, `sharing_strikes`, `person_payees`.

Not yet created: `accounts`, `recurring_expenses`, `insights`, `ai_queries`.

### 6.2 Table-to-Domain Mapping

Implemented:

| Domain entity | Database table | Primary key | Relationships |
|---|---|---|---|
| User | `users` | `id` | Root ownership entity |
| Statement | `statements` | `id` | `user_id → users.id` |
| Merchant | `merchants` | `id` | `parent_merchant_id → merchants.id` (shared, not user-owned) |
| Category | `categories` | `id` | `user_id → users.id` |
| Person | `people` | `id` | `user_id → users.id`; `category_id`, `credit_category_id → categories.id` (SET NULL) |
| Transaction | `transactions` | `id` | `user_id → users.id`; `statement_id → statements.id`; `merchant_id → merchants.id`; `person_id → people.id` (SET NULL); `category_id → categories.id` (SET NULL); `transfer_pair_id → transactions.id` (SET NULL) |
| UserMerchantCategory | `user_merchant_categories` | `(user_id, merchant_id)` | `merchant_id → merchants.id`; `category_id`, `credit_category_id → categories.id` (SET NULL) |
| UserMerchantAlias | `user_merchant_aliases` | `(user_id, payee_key)` | `user_id → users.id` (CASCADE); `merchant_id → merchants.id` |
| PersonPayee | `person_payees` | `(user_id, payee_key)` | `person_id → people.id` (CASCADE) |
| AuditLog | `audit_logs` | `id` | `user_id` (no foreign key) |

Merchant-name sharing (`merchant_suggestion_votes`, `merchant_suggestion_reports`, `hidden_merchant_suggestions`, `sharing_strikes`) and infrastructure tables (`preferences`, `refresh_tokens`, `outbox_events`, `processed_events`) are outside the financial domain.

Planned (not yet created): `accounts` (`user_id → users.id`; then `statements.account_id` and `transactions.account_id → accounts.id`), `recurring_expenses`, `insights`, `ai_queries`.

### 6.3 Constraints

Enforced today: primary keys; unique user email; ownership foreign keys; exact decimal transaction amounts (`DECIMAL(12, 2)`); non-null raw descriptions; unique `(user_id, file_hash)` on statements; unique `(user_id, transaction_fingerprint)` on transactions; category names unique per user ignoring case; merchant names unique ignoring case; person names unique per user ignoring case. Direction is a free-text `type` (`DEBIT`/`CREDIT`) without a database check; the application writes only those two values.

### 6.4 Index Baseline

Implemented: transactions on `(user_id, date)`, `(user_id, merchant_id, date)`, `merchant_id`, `statement_id`, `person_id`; statements on `(user_id, created_at)` and `status`; every category foreign key on `people` and `user_merchant_categories`; `person_payees(person_id)`; `user_merchant_aliases(merchant_id)` and unique `public_id`; audit logs on `(user_id, occurred_at)` and `(resource_type, resource_id)`. Account/date and category/date transaction indexes wait for the `accounts` table and are not needed while the effective category is derived.

### 6.5 Ownership and Tenant-Safety Boundary

Every user-owned table carries `user_id`. Categories are per user; the application treats a reference to another user's category as not set, because a foreign key cannot check that both rows share an owner. Merchants are shared by design; per-user merchant data lives in `user_merchant_categories` and `user_merchant_aliases`. Reads and writes are always scoped by the caller's `user_id`; another user's record behaves as missing (404).

### 6.6 Migration and Schema Tests

Migration validation covers clean creation from an empty PostgreSQL database, migration ordering, primary/foreign-key enforcement, unique email, statement checksum duplicate detection, transaction fingerprint duplicate/idempotency behavior, exact decimal persistence, self-referencing merchant/category relationships, baseline indexes, and rejection of references to missing parent records.

### 6.7 Documentation Synchronization

`DOMAIN-MODEL.md` is the document of record. Changes to entities, relationships, invariants, indexes, or constraints represented here must update this document in the same change. The baseline remains within scope: it maps the approved model to persistence and does not introduce new product semantics.

### 6.8 Explicitly Deferred Financial Semantics

Direction, refunds and own-account transfers are decided in section 4. The baseline does not encode rules for credit-card payments, investment contributions, cash withdrawals, or EMI principal/interest representation; these remain product/domain decisions.
