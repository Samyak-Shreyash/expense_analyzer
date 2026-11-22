-- Migration: V27__seed_rent_insurance_health_home_services.sql
-- Description: New spending default categories for users whose defaults were already seeded.

INSERT INTO categories (id, user_id, name, counts_as_spending)
SELECT gen_random_uuid(), u.id, d.name, TRUE
FROM users u
CROSS JOIN (VALUES ('Rent'), ('Insurance'), ('Health'), ('Home Services')) AS d(name)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.user_id = u.id AND lower(c.name) = lower(d.name));
