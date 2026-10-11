CREATE TABLE IF NOT EXISTS preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,
    value JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_preferences_user_key UNIQUE (user_id, key)
);

-- Index for faster lookups by user
CREATE INDEX IF NOT EXISTS idx_preferences_user_id ON preferences(user_id);
