-- Migration: V22__merchants_canonical_name_case_insensitive_unique.sql
-- Description: Merges merchants whose names differ only by case into the earliest-created one, then makes
-- canonical_name unique ignoring case. The case-sensitive uq_merchants_canonical_name stays (implied by the new index).

CREATE TEMPORARY TABLE merchant_case_duplicates ON COMMIT DROP AS
SELECT id, keep_id
FROM (SELECT id,
             FIRST_VALUE(id) OVER (PARTITION BY LOWER(canonical_name) ORDER BY created_at, id) AS keep_id
      FROM merchants) ranked
WHERE id <> keep_id;

UPDATE transactions t SET merchant_id = d.keep_id
FROM merchant_case_duplicates d WHERE t.merchant_id = d.id;

UPDATE merchants m SET parent_merchant_id = d.keep_id
FROM merchant_case_duplicates d WHERE m.parent_merchant_id = d.id;

-- Per-user (user, merchant) mappings: keep one row per user for the surviving merchant (an existing one wins).
INSERT INTO user_merchant_categories (user_id, merchant_id, category_id)
SELECT DISTINCT ON (c.user_id, d.keep_id) c.user_id, d.keep_id, c.category_id
FROM user_merchant_categories c
JOIN merchant_case_duplicates d ON d.id = c.merchant_id
ORDER BY c.user_id, d.keep_id, c.category_id
ON CONFLICT (user_id, merchant_id) DO NOTHING;

DELETE FROM user_merchant_categories c
USING merchant_case_duplicates d WHERE c.merchant_id = d.id;

-- Aliases and votes are keyed by (user, payee), so repointing cannot collide.
UPDATE user_merchant_aliases a SET merchant_id = d.keep_id
FROM merchant_case_duplicates d WHERE a.merchant_id = d.id;

UPDATE merchant_suggestion_votes v SET merchant_id = d.keep_id
FROM merchant_case_duplicates d WHERE v.merchant_id = d.id;

DELETE FROM merchants m
USING merchant_case_duplicates d WHERE m.id = d.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchants_canonical_name_lower ON merchants (LOWER(canonical_name));
