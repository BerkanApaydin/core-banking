-- V38__idempotency_key_kind.sql
-- Split the idempotency_keys key space into an explicit discriminator instead
-- of string-prefix matching: HTTP request keys vs outbox handler dedup keys.
-- The old left(key_value, ...) predicates could not use an index and the two
-- retention policies shared one key space by convention only.
--
-- Backfill uses an ESCAPED like pattern: bare underscores are single-char
-- wildcards, so 'outbox_handler_%' would also match near-misses such as
-- 'outboxXhandlerX...'. New writes set key_kind explicitly in tryInsert; the
-- NOT NULL DEFAULT keeps legacy raw writers working (they land as HTTP).

ALTER TABLE idempotency_keys
    ADD COLUMN key_kind VARCHAR(16) NOT NULL DEFAULT 'HTTP';

UPDATE idempotency_keys
    SET key_kind = 'HANDLER'
    WHERE key_value LIKE 'outbox\_handler\_%' ESCAPE '\';

ALTER TABLE idempotency_keys
    ADD CONSTRAINT chk_idempotency_key_kind CHECK (key_kind IN ('HTTP', 'HANDLER'));

-- Cleanup deletes filter (key_kind, created_at) with status <> 'PENDING'.
CREATE INDEX IF NOT EXISTS idx_idempotency_cleanup_kind_created
    ON idempotency_keys (key_kind, created_at)
    WHERE status <> 'PENDING';

-- Backlog HTTP-pending scan (status = 'PENDING' AND key_kind = 'HTTP').
CREATE INDEX IF NOT EXISTS idx_idempotency_pending_http_kind
    ON idempotency_keys (created_at)
    WHERE status = 'PENDING' AND key_kind = 'HTTP';
