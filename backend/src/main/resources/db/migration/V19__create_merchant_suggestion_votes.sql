-- Migration: V19__create_merchant_suggestion_votes.sql
-- Description: Opt-in votes that a payee key is a given merchant. Holds no amounts, dates, categories or descriptions.

CREATE TABLE IF NOT EXISTS merchant_suggestion_votes (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_merchant_suggestion_votes_key_merchant ON merchant_suggestion_votes(payee_key, merchant_id);
