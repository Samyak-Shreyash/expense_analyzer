-- Migration: V19__create_loan_payees.sql
-- Description: A lender's default loan; the user's future EMIs to this payee link to it.

CREATE TABLE IF NOT EXISTS loan_payees (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(100) NOT NULL,
    loan_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_loan_payees_loan_id ON loan_payees(loan_id);
