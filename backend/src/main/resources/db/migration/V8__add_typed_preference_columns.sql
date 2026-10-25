-- Preference entity maps typed columns that V6 never created.
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS theme VARCHAR(20);
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS currency VARCHAR(10);
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS notification_mode VARCHAR(20);
