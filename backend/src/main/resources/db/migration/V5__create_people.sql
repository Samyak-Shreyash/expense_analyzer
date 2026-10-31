-- Migration: V5__create_people.sql
-- Description: People a user transfers money to, with money-out and money-in default categories.

CREATE TABLE IF NOT EXISTS people (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    credit_category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_people_user_lower_name ON people(user_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_people_category_id ON people(category_id);
CREATE INDEX IF NOT EXISTS idx_people_credit_category_id ON people(credit_category_id);
