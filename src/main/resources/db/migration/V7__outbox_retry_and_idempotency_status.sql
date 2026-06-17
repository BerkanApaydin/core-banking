-- Outbox retry / dead letter support
ALTER TABLE outbox_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN dead_letter BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(2000);

CREATE INDEX idx_outbox_events_pending ON outbox_events (processed, dead_letter, created_at);

-- Idempotency HTTP status preservation
ALTER TABLE idempotency_keys ADD COLUMN response_status INT;
