-- Migration: V3__add_foreign_keys.sql
-- Description: Add foreign key constraints between transactions and statements

-- Foreign key: transactions.user_id -> users.id
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id) REFERENCES users(id);

-- Foreign key: transactions.statement_id -> statements.id
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_statement
    FOREIGN KEY (statement_id) REFERENCES statements(id);
