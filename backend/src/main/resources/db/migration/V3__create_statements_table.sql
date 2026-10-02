-- Migration: V3__create_statements_table.sql
-- Description: Create statements table for statement management

CREATE TABLE IF NOT EXISTS statements (
    id UUID PRIMARY KEY,
    description VARCHAR(2048) NOT NULL,
    amount_cents DECIMAL(12, 2) NOT NULL,
    date TIMESTAMP NOT NULL,
    type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
