-- Migration: V18__create_person_payees.sql
-- Description: Payee names (upper-cased) grouped under a person.

CREATE TABLE IF NOT EXISTS person_payees (
    user_id UUID NOT NULL REFERENCES users(id),
    payee_key VARCHAR(100) NOT NULL,
    person_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, payee_key)
);

CREATE INDEX IF NOT EXISTS idx_person_payees_person_id ON person_payees(person_id);
