-- Migration: V25__create_person_payees.sql
-- Description: Payee names (upper-cased, as parsed from UPI transfers) grouped under a person, so several names can
-- belong to one person and a renamed or merged person keeps receiving later imports. Each existing person starts with
-- their own name, keyed the way the app keys names (trimmed, whitespace collapsed, upper-cased).

CREATE TABLE IF NOT EXISTS person_payees (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(100) NOT NULL,
    person_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_person_payees_person_id ON person_payees(person_id);

INSERT INTO person_payees (user_id, payee_key, person_id)
SELECT user_id, upper(regexp_replace(trim(name), '\s+', ' ', 'g')), id FROM people
ON CONFLICT DO NOTHING;
