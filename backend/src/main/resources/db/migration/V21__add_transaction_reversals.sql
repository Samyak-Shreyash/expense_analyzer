-- Migration: V21__add_transaction_reversals.sql
-- Description: A failed payment and the refund that reversed it, paired by the user; both are left out of analytics.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reversal_pair_id UUID REFERENCES transactions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_reversal_pair_id ON transactions(reversal_pair_id);
