-- Migration: V3__create_merchants.sql
-- Description: Shared merchant catalogue; names are unique ignoring case.

CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    parent_merchant_id UUID REFERENCES merchants(id),
    category_hint VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchants_canonical_name UNIQUE (canonical_name)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchants_canonical_name_lower ON merchants (LOWER(canonical_name));
