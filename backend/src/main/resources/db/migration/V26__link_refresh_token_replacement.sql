-- A rotated token points at its replacement until the client first uses it. Until then a replay of the rotated token
-- (its response was lost, e.g. on a page reload) supersedes the replacement instead of failing.
-- Replaces replayed_at from V25, which allowed only one such replay.
ALTER TABLE refresh_tokens DROP COLUMN replayed_at;
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_id UUID;
