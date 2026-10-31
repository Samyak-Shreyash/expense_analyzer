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
