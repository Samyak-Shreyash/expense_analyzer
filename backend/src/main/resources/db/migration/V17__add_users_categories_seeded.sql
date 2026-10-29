ALTER TABLE users ADD COLUMN categories_seeded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET categories_seeded = TRUE WHERE id IN (SELECT DISTINCT user_id FROM categories);
