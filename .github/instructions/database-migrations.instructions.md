---
applyTo: "backend/src/main/resources/db/migration/**"
---
# Flyway Migrations — Expense Analyzer

- Use `V<n>__<snake_case_description>.sql` files in `backend/src/main/resources/db/migration/`.
- Add forward migrations only. Never alter a migration that may have run in a shared environment.
- Target PostgreSQL: uppercase SQL keywords, lowercase `snake_case` identifiers, UUID application IDs, `TIMESTAMP WITH TIME ZONE`, explicit constraints, foreign keys, and needed indexes.
- Store money as `NUMERIC(19,4)` (or documented compatible precision), never floating-point types.
- `ddl-auto: validate` means Flyway migrations define schema changes.
- Avoid destructive operations, irreversible rewrites, and real financial/customer seed data without explicit approval and a rollout plan.
- Add indexes for foreign keys and actual query paths, not speculative paths.
- Enforce user ownership in application services until a database tenant-isolation strategy is intentionally implemented; do not claim RLS is configured.
