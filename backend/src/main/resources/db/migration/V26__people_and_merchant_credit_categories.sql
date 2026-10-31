-- Migration: V26__people_and_merchant_credit_categories.sql
-- Description: Separate default categories for money in (credit_category_id) and money out (category_id) on people
-- and merchant mappings. Existing defaults are copied to money in so nothing changes until the user edits them.

ALTER TABLE people ADD COLUMN IF NOT EXISTS credit_category_id UUID REFERENCES categories(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_people_credit_category_id ON people(credit_category_id);
UPDATE people SET credit_category_id = category_id;

ALTER TABLE user_merchant_categories ADD COLUMN IF NOT EXISTS credit_category_id UUID
    REFERENCES categories(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_user_merchant_categories_credit_category_id
    ON user_merchant_categories(credit_category_id);
UPDATE user_merchant_categories SET credit_category_id = category_id;

-- A mapping may now hold only one direction; deleting one category must not drop the other (was ON DELETE CASCADE).
-- The app deletes mappings left with neither category.
ALTER TABLE user_merchant_categories ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE user_merchant_categories DROP CONSTRAINT IF EXISTS user_merchant_categories_category_id_fkey;
ALTER TABLE user_merchant_categories ADD CONSTRAINT user_merchant_categories_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;
