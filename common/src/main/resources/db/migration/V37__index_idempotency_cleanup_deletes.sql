-- V37: make the idempotency-cleanup deletes indexable (F-09).
--
-- IdempotencyKeyJpaRepository multiplexes HTTP keys and outbox-handler dedup
-- keys in one table, distinguished by the 'outbox_handler_' prefix. The two
-- cleanup deletes filter status <> 'PENDING' AND created_at < :threshold AND
-- a left(key_value, ...) prefix predicate; only (created_at) was indexed, so
-- every nightly cleanup scanned retained rows of both key kinds. These partial
-- indexes mirror the two delete predicates exactly (prefix length 16 =
-- length('outbox_handler_')).
CREATE INDEX IF NOT EXISTS idx_idempotency_cleanup_http
    ON idempotency_keys (created_at)
    WHERE status <> 'PENDING' AND left(key_value, 16) <> 'outbox_handler_';

CREATE INDEX IF NOT EXISTS idx_idempotency_cleanup_handler
    ON idempotency_keys (created_at)
    WHERE status <> 'PENDING' AND left(key_value, 16) = 'outbox_handler_';
