-- Migration: V9__add_statement_upload_columns.sql
-- Description: Columns for uploaded statement files (#229).
-- Account fields are filled after parsing, so they become nullable.
-- The new columns are added nullable because existing rows cannot satisfy NOT NULL and
-- nothing is deleted here. The Statement entity marks original_filename, content_type,
-- size_bytes, sha256, object_key and status as nullable=false, so every new row sets them.

ALTER TABLE statements ALTER COLUMN account_number DROP NOT NULL;
ALTER TABLE statements ALTER COLUMN account_type DROP NOT NULL;

ALTER TABLE statements ADD COLUMN original_filename VARCHAR(255);
ALTER TABLE statements ADD COLUMN content_type VARCHAR(100);
ALTER TABLE statements ADD COLUMN size_bytes BIGINT;
ALTER TABLE statements ADD COLUMN sha256 CHAR(64);
ALTER TABLE statements ADD COLUMN object_key VARCHAR(512);
ALTER TABLE statements ADD COLUMN status VARCHAR(20);
ALTER TABLE statements ADD COLUMN error_code VARCHAR(64);

ALTER TABLE statements ADD CONSTRAINT uk_statements_user_sha256 UNIQUE (user_id, sha256);
