-- Migration: V1__create_transactions_table.sql
-- Description: Create transactions table with indexes for user and date queries

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_description VARCHAR(2048) NOT NULL,
    normalized_description VARCHAR(1024),
    merchant_id UUID,
    category_id UUID,
    amount_cents DECIMAL(12, 2) NOT NULL,
    date TIMESTAMP NOT NULL,
    type VARCHAR(10) NOT NULL,
    user_id UUID NOT NULL,
    statement_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, date);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant_id ON transactions(merchant_id);
