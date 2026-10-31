-- Migration: V2__create_statements.sql
-- Description: Uploaded bank statements and their processing state.

CREATE TABLE IF NOT EXISTS statements (
    id UUID PRIMARY KEY,
    account_number VARCHAR(50),
    bank_name VARCHAR(255),
    account_type VARCHAR(50),
    currency_code VARCHAR(3),
    user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    period_start_date DATE,
    period_end_date DATE,
    file_name VARCHAR(255),
    storage_key VARCHAR(512),
    file_hash VARCHAR(64),
    format VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    processing_error VARCHAR(100),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statements_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_statements_user_file_hash UNIQUE (user_id, file_hash)
);

CREATE INDEX IF NOT EXISTS idx_statements_user_created ON statements(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_statements_status ON statements(status);
