-- Migration: V9__add_statement_upload_columns.sql
-- Description: Track uploaded statement files (storage key, hash, format, processing status).
-- Upload does not know account details, so those columns become optional.

ALTER TABLE statements ALTER COLUMN account_number DROP NOT NULL;
ALTER TABLE statements ALTER COLUMN account_type DROP NOT NULL;
ALTER TABLE statements ALTER COLUMN currency_code DROP NOT NULL;

ALTER TABLE statements ADD COLUMN file_name VARCHAR(255);
ALTER TABLE statements ADD COLUMN storage_key VARCHAR(512);
ALTER TABLE statements ADD COLUMN file_hash VARCHAR(64);
ALTER TABLE statements ADD COLUMN format VARCHAR(10);
ALTER TABLE statements ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED';
ALTER TABLE statements ADD COLUMN processing_error VARCHAR(100);
ALTER TABLE statements ADD COLUMN completed_at TIMESTAMP;

-- Identical files are duplicates within one user's data (NULL hashes on legacy rows don't collide).
ALTER TABLE statements ADD CONSTRAINT uq_statements_user_file_hash UNIQUE (user_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_statements_user_created ON statements(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_statements_status ON statements(status);
