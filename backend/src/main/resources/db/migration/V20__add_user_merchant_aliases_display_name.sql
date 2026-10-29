-- Migration: V20__add_user_merchant_aliases_display_name.sql
-- Description: The merchant name exactly as the user typed it, shown to that user instead of the shared canonical spelling.

ALTER TABLE user_merchant_aliases ADD COLUMN display_name VARCHAR(100);

UPDATE user_merchant_aliases a
SET display_name = LEFT(m.canonical_name, 100)
FROM merchants m
WHERE m.id = a.merchant_id;

ALTER TABLE user_merchant_aliases ALTER COLUMN display_name SET NOT NULL;
