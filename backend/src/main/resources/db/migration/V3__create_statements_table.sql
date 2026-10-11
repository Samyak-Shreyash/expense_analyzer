-- Migration: V3__create_statements_table.sql
-- Description: Create statements table for statement management

CREATE TABLE IF NOT EXISTS statements (
    id UUID PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL,
    bank_name VARCHAR(255),
    account_type VARCHAR(50) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    period_start_date DATE,
    period_end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
