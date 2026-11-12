-- Set when a rotated token is replayed inside the grace window (its replacement was lost, e.g. on a page reload).
-- One such replay is allowed per token; a second one is refused.
ALTER TABLE refresh_tokens ADD COLUMN replayed_at TIMESTAMP WITH TIME ZONE;
