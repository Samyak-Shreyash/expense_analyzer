-- Migration: V1__create_users.sql
-- Description: Application users.

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    full_name VARCHAR(255),
    preferences JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    categories_seeded BOOLEAN NOT NULL DEFAULT FALSE,
    share_merchant_names BOOLEAN NOT NULL DEFAULT FALSE,
    sharing_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_email UNIQUE (email)
);
