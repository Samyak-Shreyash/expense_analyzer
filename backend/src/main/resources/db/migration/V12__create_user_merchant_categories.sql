-- Migration: V12__create_user_merchant_categories.sql
-- Description: A user's money-out and money-in categories for a merchant.

CREATE TABLE IF NOT EXISTS user_merchant_categories (
    user_id UUID NOT NULL REFERENCES users(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    -- SET NULL, not CASCADE: deleting one direction's category must keep the other; the app drops empty rows
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    credit_category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_user_merchant_categories_category_id ON user_merchant_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_user_merchant_categories_credit_category_id
    ON user_merchant_categories(credit_category_id);
