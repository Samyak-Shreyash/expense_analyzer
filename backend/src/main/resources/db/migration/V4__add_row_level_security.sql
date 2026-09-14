-- Migration: V4__add_row_level_security.sql
-- Description: Add Row Level Security (RLS) policies for tenant isolation

-- RLS for transactions table
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;

CREATE POLICY transactions_tenant_isolation ON transactions
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- RLS for statements table
ALTER TABLE statements ENABLE ROW LEVEL SECURITY;
ALTER TABLE statements FORCE ROW LEVEL SECURITY;

CREATE POLICY statements_tenant_isolation ON statements
    USING (user_id = current_setting('app.current_user_id', true)::uuid);
