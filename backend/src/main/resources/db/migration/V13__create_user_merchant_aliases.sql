-- Migration: V13__create_user_merchant_aliases.sql
-- Description: A user's own merchant name for a payee key.

CREATE TABLE IF NOT EXISTS user_merchant_aliases (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    display_name VARCHAR(100) NOT NULL,
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    share_opt_out BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_user_merchant_aliases_merchant_id ON user_merchant_aliases(merchant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_merchant_aliases_public_id ON user_merchant_aliases(public_id);
