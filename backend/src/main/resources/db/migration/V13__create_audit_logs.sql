-- Migration: V13__create_audit_logs.sql
-- Description: Append-only security audit trail (LLD section 5.10). Holds ids and action codes only:
-- no emails, file names, amounts, descriptions or tokens.

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    correlation_id VARCHAR(100),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_occurred ON audit_logs(user_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
