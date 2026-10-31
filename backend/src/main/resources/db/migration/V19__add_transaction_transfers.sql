-- Migration: V19__add_transaction_transfers.sql
-- Description: Money moved between a user's own accounts (DOMAIN-MODEL 4.3). transfer_manual records that the user
-- marked or unmarked the row, so automatic matching leaves it alone.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transfer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transfer_manual BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transfer_pair_id UUID REFERENCES transactions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_transfer_pair_id ON transactions(transfer_pair_id);
