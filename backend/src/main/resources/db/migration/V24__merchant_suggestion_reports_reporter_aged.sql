-- Migration: V24__merchant_suggestion_reports_reporter_aged.sql
-- Description: Records whether the reporter's account was old enough (7 days) when the report was filed, so a report
-- never starts counting just because the account aged later. Existing rows are judged against their own filing time.

ALTER TABLE merchant_suggestion_reports ADD COLUMN reporter_aged BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE merchant_suggestion_reports r SET reporter_aged = TRUE
FROM users u WHERE u.id = r.user_id AND u.created_at <= r.created_at - INTERVAL '7 days';
