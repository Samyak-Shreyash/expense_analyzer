-- Migration: V17__create_sharing_strikes.sql
-- Description: Strikes against users whose shared names were hidden.

CREATE TABLE IF NOT EXISTS sharing_strikes (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key, merchant_id)
);
