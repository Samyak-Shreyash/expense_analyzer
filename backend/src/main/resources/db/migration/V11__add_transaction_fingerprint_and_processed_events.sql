-- Migration: V11__add_transaction_fingerprint_and_processed_events.sql
-- Description: Anti-duplication for imported transactions (LLD section 10) and consumer
-- idempotency for Kafka events (LLD section 24).

ALTER TABLE transactions ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE transactions ADD COLUMN transaction_fingerprint VARCHAR(64);

-- One row per fingerprint within a user's data; NULLs (manually created rows) don't collide.
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_user_fingerprint UNIQUE (user_id, transaction_fingerprint);
CREATE INDEX IF NOT EXISTS idx_transactions_statement_id ON transactions(statement_id);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer)
);
