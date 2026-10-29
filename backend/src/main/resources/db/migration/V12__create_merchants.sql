-- Migration: V12__create_merchants.sql
-- Description: Canonical merchants for normalised transactions (LLD sections 5.5 and 11).

CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    parent_merchant_id UUID REFERENCES merchants(id),
    category_hint VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchants_canonical_name UNIQUE (canonical_name)
);

ALTER TABLE transactions ADD COLUMN merchant_confidence NUMERIC(3, 2);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_merchant_id FOREIGN KEY (merchant_id) REFERENCES merchants(id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_merchant_date ON transactions(user_id, merchant_id, date);
