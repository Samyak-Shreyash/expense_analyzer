-- Migration: V5__add_foreign_keys.sql
-- Description: Add foreign key constraints to statements table

ALTER TABLE statements ADD CONSTRAINT fk_statements_user_id
    FOREIGN KEY (user_id) REFERENCES users(id);
