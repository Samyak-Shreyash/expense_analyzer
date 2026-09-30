-- Migration: V5__add_row_level_security.sql
-- Description: Add row-level security policy for transactions

CREATE POLICY users_can_view_own_transactions ON transactions
    FOR SELECT
    USING (user_id = current_setting('app.row_level_security.user_id')::uuid);
