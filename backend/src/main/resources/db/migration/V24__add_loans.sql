-- Loans: an EMI (DEBIT) links to the loan it repays (a CREDIT); the part of an EMI paid beyond the principal is
-- interest. A lender's default loan links its future EMIs; a closed loan takes no more.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS loan_id UUID REFERENCES transactions(id) ON DELETE SET NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS loan_interest_paise DECIMAL(12, 2);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS loan_closed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_transactions_loan_id ON transactions(loan_id);

CREATE TABLE IF NOT EXISTS loan_payees (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(100) NOT NULL,
    loan_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, payee_key)
);
CREATE INDEX IF NOT EXISTS idx_loan_payees_loan_id ON loan_payees(loan_id);

-- New non-spending default for users whose defaults were already seeded.
INSERT INTO categories (id, user_id, name, counts_as_spending)
SELECT gen_random_uuid(), u.id, 'Loan EMI', FALSE FROM users u
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.user_id = u.id AND lower(c.name) = 'loan emi');
