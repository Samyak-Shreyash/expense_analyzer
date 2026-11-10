-- Which app exported the file (BANK = a bank statement); the user picks it at upload and it selects the parser.
ALTER TABLE statements ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'BANK';
-- The 12-digit UPI reference (RRN) found in a row; a UPI app's row and the bank's row for one payment share it.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS upi_ref VARCHAR(12);
CREATE INDEX IF NOT EXISTS idx_transactions_user_upi_ref ON transactions(user_id, upi_ref);
