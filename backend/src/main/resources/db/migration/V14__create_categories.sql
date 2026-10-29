-- Migration: V14__create_categories.sql
-- Description: User-managed spending categories. Defaults are seeded per user on first access, not here.

CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL,
    counts_as_spending BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_user_lower_name ON categories(user_id, lower(name));

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_category_id
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;
