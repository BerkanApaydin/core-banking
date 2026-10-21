-- Backlog monitoring must not scan retained outbox-handler dedup entries.
-- Only HTTP PENDING keys participate in the oldest-request-age metric.
CREATE INDEX IF NOT EXISTS idx_idempotency_pending_http_created_at
    ON idempotency_keys (created_at)
    WHERE status = 'PENDING' AND left(key_value, 5) = 'http_';
