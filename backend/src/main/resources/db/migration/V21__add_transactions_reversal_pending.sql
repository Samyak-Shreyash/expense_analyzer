-- A payment or refund the user marked as failed/refunded before its other half was paired.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reversal_pending BOOLEAN NOT NULL DEFAULT FALSE;
