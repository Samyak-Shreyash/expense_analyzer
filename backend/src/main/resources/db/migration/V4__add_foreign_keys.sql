-- Migration: V4__add_foreign_keys.sql
-- Description: Add foreign key constraints to transactions table

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_user_id
    FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_statement_id
    FOREIGN KEY (statement_id) REFERENCES statements(id);
