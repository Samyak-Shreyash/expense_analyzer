-- Migration: V23__add_transaction_reversal_charges.sql
-- Description: What a failed payment cost when its refund came back smaller; this part still counts as spending.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reversal_charge_paise DECIMAL(12, 2);
