-- Migration: V16__create_hidden_merchant_suggestions.sql
-- Description: Suggestions hidden for everyone after enough reports.

CREATE TABLE IF NOT EXISTS hidden_merchant_suggestions (
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    hidden_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payee_key, merchant_id)
);
