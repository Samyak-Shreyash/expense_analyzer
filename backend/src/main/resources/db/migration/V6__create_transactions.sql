-- Migration: V6__create_transactions.sql
-- Description: Imported transactions. Raw data is kept as imported.

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    raw_description VARCHAR(2048) NOT NULL,
    normalized_description VARCHAR(1024),
    merchant_id UUID,
    merchant_confidence NUMERIC(3, 2),
    category_id UUID,
    person_id UUID,
    amount_cents DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    date TIMESTAMP NOT NULL,
    type VARCHAR(10) NOT NULL,
    user_id UUID NOT NULL,
    statement_id UUID,
    transaction_fingerprint VARCHAR(64),
    -- Money moved between a user's own accounts. transfer_manual records that the user marked or unmarked the row,
    -- so automatic matching leaves it alone.
    transfer BOOLEAN NOT NULL DEFAULT FALSE,
    transfer_manual BOOLEAN NOT NULL DEFAULT FALSE,
    transfer_pair_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    -- A failed payment and the refund that reversed it, paired by the user; both are left out of analytics.
    reversal_pair_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    -- What a failed payment cost when its refund came back smaller; this part still counts as spending.
    reversal_charge_paise DECIMAL(12, 2),
    -- A payment or refund the user marked as failed/refunded before its other half was paired.
    reversal_pending BOOLEAN NOT NULL DEFAULT FALSE,
    -- The 12-digit UPI reference (RRN) found in a row; a UPI app's row and the bank's row for one payment share it.
    upi_ref VARCHAR(12),
    -- A row the user marked as a duplicate of another (e.g. the same payment from two sources); left out of analytics.
    duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    -- Loans: an EMI (DEBIT) links to the loan it repays (a CREDIT); the part of an EMI paid beyond the principal is
    -- interest. A closed loan takes no more EMIs.
    loan_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    loan_interest_paise DECIMAL(12, 2),
    loan_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_transactions_statement_id FOREIGN KEY (statement_id) REFERENCES statements(id),
    CONSTRAINT fk_transactions_merchant_id FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT fk_transactions_category_id FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT fk_transactions_person_id FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE SET NULL,
    CONSTRAINT uq_transactions_user_fingerprint UNIQUE (user_id, transaction_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, date);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant_id ON transactions(merchant_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_merchant_date ON transactions(user_id, merchant_id, date);
CREATE INDEX IF NOT EXISTS idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX IF NOT EXISTS idx_transactions_person_id ON transactions(person_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transfer_pair_id ON transactions(transfer_pair_id);
CREATE INDEX IF NOT EXISTS idx_transactions_reversal_pair_id ON transactions(reversal_pair_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_upi_ref ON transactions(user_id, upi_ref);
CREATE INDEX IF NOT EXISTS idx_transactions_loan_id ON transactions(loan_id);
