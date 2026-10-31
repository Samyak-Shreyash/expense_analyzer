-- Migration: V15__create_merchant_suggestion_reports.sql
-- Description: Reports of offensive or wrong suggestions; reporter_aged records whether the reporter was old enough when filing.

CREATE TABLE IF NOT EXISTS merchant_suggestion_reports (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payee_key VARCHAR(255) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    reporter_aged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, payee_key, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_merchant_suggestion_reports_pair ON merchant_suggestion_reports(payee_key, merchant_id);
