-- Migration: V16__create_people.sql
-- Description: People a user transfers money to (detected from UPI P2A/P2P transfers or set by the user).

CREATE TABLE IF NOT EXISTS people (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_people_user_lower_name ON people(user_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_people_category_id ON people(category_id);

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS person_id UUID;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_person_id
    FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_person_id ON transactions(person_id);
