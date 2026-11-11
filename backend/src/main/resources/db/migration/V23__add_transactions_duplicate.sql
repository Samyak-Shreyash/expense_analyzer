-- A row the user marked as a duplicate of another (e.g. the same payment from two sources); left out of analytics.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS duplicate BOOLEAN NOT NULL DEFAULT FALSE;
