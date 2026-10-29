-- Migration: V18__create_user_merchant_aliases.sql
-- Description: A user's own merchant name for a payee that no built-in rule recognises, keyed by payee.

CREATE TABLE IF NOT EXISTS user_merchant_aliases (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_user_merchant_aliases_merchant_id ON user_merchant_aliases(merchant_id);
