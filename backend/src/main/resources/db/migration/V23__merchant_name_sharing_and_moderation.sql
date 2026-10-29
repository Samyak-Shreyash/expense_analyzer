-- Migration: V23__merchant_name_sharing_and_moderation.sql
-- Description: Account-level merchant-name sharing (default off), per-alias opt-out, and moderation of shared
-- suggestions (reports, hidden pairs, strikes, loss of sharing). Holds no amounts, dates or descriptions.

ALTER TABLE users ADD COLUMN share_merchant_names BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN sharing_revoked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_merchant_aliases ADD COLUMN share_opt_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_merchant_aliases ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX uq_user_merchant_aliases_public_id ON user_merchant_aliases(public_id);

-- Sharing is now an account setting that starts off for everyone, so nothing may stay shared.
DELETE FROM merchant_suggestion_votes;

CREATE TABLE merchant_suggestion_reports (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key, merchant_id)
);
CREATE INDEX idx_merchant_suggestion_reports_pair ON merchant_suggestion_reports(payee_key, merchant_id);

CREATE TABLE hidden_merchant_suggestions (
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    hidden_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payee_key, merchant_id)
);

CREATE TABLE sharing_strikes (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key, merchant_id)
);
