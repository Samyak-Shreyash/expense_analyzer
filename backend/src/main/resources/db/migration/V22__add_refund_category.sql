-- Migration: V22__add_refund_category.sql
-- Description: Gives existing users the Refund category, the default for money in from a merchant.

INSERT INTO categories (id, user_id, name, counts_as_spending)
SELECT gen_random_uuid(), id, 'Refund', TRUE FROM users
ON CONFLICT (user_id, lower(name)) DO NOTHING;
