-- Migration: V15__create_user_merchant_categories.sql
-- Description: A user's own category for a merchant; applies to all of that user's transactions with the merchant.

CREATE TABLE IF NOT EXISTS user_merchant_categories (
    user_id UUID NOT NULL REFERENCES users(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_user_merchant_categories_category_id ON user_merchant_categories(category_id);
