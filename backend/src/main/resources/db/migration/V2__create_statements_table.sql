-- Migration: V2__create_statements_table.sql
-- Description: Create statements table for bank account management

CREATE TABLE IF NOT EXISTS statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(50) NOT NULL,
    bank_name VARCHAR(255),
    account_type VARCHAR(50) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    user_id UUID NOT NULL,
    period_start_date DATE,
    period_end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for performance optimization
CREATE INDEX IF NOT EXISTS idx_statements_user_id ON statements(user_id);
