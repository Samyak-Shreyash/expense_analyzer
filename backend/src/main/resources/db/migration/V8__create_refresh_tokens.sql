-- Migration: V8__create_refresh_tokens.sql
-- Description: Refresh tokens, stored as hashes.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 hex of the raw token; the raw value is only ever in the client's cookie
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
