-- Migration: V10__create_outbox_events.sql
-- Description: Transactional outbox for domain events published to Kafka (LLD section 23).

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    user_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(255)
);

-- The publisher polls unpublished rows in creation order.
CREATE INDEX IF NOT EXISTS idx_outbox_events_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;
