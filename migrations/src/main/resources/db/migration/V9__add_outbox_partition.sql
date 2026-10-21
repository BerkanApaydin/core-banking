ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS partition INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_outbox_events_partition ON outbox_events(partition, processed, dead_letter, created_at);
