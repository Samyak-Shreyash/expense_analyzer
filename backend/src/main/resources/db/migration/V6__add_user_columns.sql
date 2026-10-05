-- Add missing columns to users table referenced in auth flow but not present in schema
-- Columns: preferences (JSONB), last_login_at (TIMESTAMP WITH TIME ZONE)

ALTER TABLE users ADD COLUMN IF NOT EXISTS preferences JSONB;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;
