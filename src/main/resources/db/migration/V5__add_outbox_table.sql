-- V5__add_outbox_table.sql
-- Create outbox_events table for Transactional Outbox pattern
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    processed BOOLEAN NOT NULL,
    processed_at TIMESTAMP(6)
);

CREATE INDEX idx_outbox_events_unprocessed ON outbox_events (processed, created_at);
