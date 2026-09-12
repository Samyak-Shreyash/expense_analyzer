---
applyTo: "backend/src/main/resources/db/migration/**"
---

# Flyway Migrations — Expense Analyzer

## File Naming

- `V<n>__<snake_case_description>.sql` — e.g. `V1__init.sql`, `V7__add_budgets_table.sql`.
- **Never edit a migration that has been merged to `main`.** Flyway checksums will break every environment. Add a new migration instead.
- One logical change per migration. A single migration may contain multiple DDL statements for one cohesive change.

## SQL Style

- **Uppercase SQL keywords** (`CREATE TABLE`, `NOT NULL`, `PRIMARY KEY`).
- **Lowercase snake_case identifiers** (`user_id`, `created_at`).
- Table names plural (`transactions`, `user_accounts`). Junction tables: `<a>_<b>` singular (`transaction_categories`).
- Primary key: `id UUID PRIMARY KEY`.
- Timestamps: `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- Money: `amount NUMERIC(19,4) NOT NULL`, `currency CHAR(3) NOT NULL`.
- Boolean columns: `is_<adjective>` (`is_active`), never nullable — use `NOT NULL DEFAULT false`.
- Foreign keys: `REFERENCES <table>(id) ON DELETE RESTRICT` by default. Use `CASCADE` only when ownership is total.

## Mandatory Conventions

- **Every tenant-scoped table has `user_id UUID NOT NULL REFERENCES users(id)`** and RLS enabled:
```sql
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;

CREATE POLICY transactions_tenant_isolation ON transactions
    USING (user_id = current_setting('app.current_user_id', true)::uuid);
```
- **Every table has an explicit primary key** and, where useful, a unique constraint enforcing business invariants (e.g., `UNIQUE (user_id, dedupe_hash)`).
- **Index every foreign key** and every column used in a `WHERE` on a hot path.
- **Add `NOT NULL` from the start.** If backfilling, use a two-migration approach: (1) add nullable + backfill, (2) set `NOT NULL`.

## Zero-Downtime Rules (production)

- **Additive changes only** for non-breaking deploys: new tables, new nullable columns, new indexes (`CREATE INDEX CONCURRENTLY`).
- **`CREATE INDEX CONCURRENTLY`** for large tables — but note: Flyway runs in a transaction by default; use `-- flyway:executeInTransaction=false` at the top of the file when needed.
- **No `ALTER COLUMN ... SET NOT NULL`** on a live table without a backfill + `NOT VALID` constraint first.
- **No `DROP COLUMN`** in the same deploy that stops using it — deprecate first, drop in a later release.
- **No `ALTER TABLE ... ADD COLUMN ... NOT NULL` without a default** on tables with rows.

## Data Backfills

- Separate migration file from schema change.
- Batch updates with `LIMIT` to avoid long locks:
```sql
-- flyway:executeInTransaction=false
DO $$
DECLARE rows_updated INT;
BEGIN
  LOOP
    UPDATE transactions SET normalized_description = lower(description)
    WHERE normalized_description IS NULL
    LIMIT 10000;
    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    EXIT WHEN rows_updated = 0;
    COMMIT;
  END LOOP;
END $$;
```
- Prefer a backfill job over a migration for very large tables.

## What Copilot Must Never Generate

- `DROP TABLE` or `DROP COLUMN` in a normal migration without a documented ADR.
- `TRUNCATE` in any migration.
- String-concatenated SQL with user input — migrations are static.
- Missing RLS on a tenant-scoped table.
- Missing `NOT NULL` / defaults on new required columns.
- Missing indexes on foreign keys.
- Edits to an existing `V*.sql` file.
- `SERIAL`/`BIGSERIAL` — use `UUID` (v7 generated in app code) or `GENERATED ALWAYS AS IDENTITY` for internal counters.

## Seed Data

- **Never** seed production with real data. Test data goes in `src/test/resources/` or Fixture classes.
- Reference data (e.g., default categories) can be seeded via a `V<n>__seed_default_categories.sql` migration — idempotent, using `INSERT ... ON CONFLICT DO NOTHING`.

## Verification

- Every migration is validated by CI (`./gradlew flywayValidate`) against a fresh Postgres in Testcontainers.
- Integration tests spin up Postgres + run migrations + exercise the new schema.
- Rollback is by writing a **new forward migration**, not by `flyway undo` (which is not enabled in production).