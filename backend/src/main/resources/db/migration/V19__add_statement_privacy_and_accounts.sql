-- Migration: V19__add_statement_privacy_and_accounts.sql
-- Description: Delete-original-after-import preference, detected account digits on statements, and the user's own
-- account numbers.

ALTER TABLE users ADD COLUMN IF NOT EXISTS strip_statement_pii BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE statements ADD COLUMN IF NOT EXISTS detected_account VARCHAR(4);

CREATE TABLE IF NOT EXISTS user_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_number VARCHAR(50) NOT NULL,
    bank_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_accounts_user_number UNIQUE (user_id, account_number)
);

CREATE INDEX IF NOT EXISTS idx_user_accounts_user_id ON user_accounts(user_id);
