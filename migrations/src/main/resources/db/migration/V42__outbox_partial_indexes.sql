-- V42__outbox_partial_indexes.sql
--
-- Partial indexes for the outbox hot paths (DB-4). The poller runs every
-- ~2s with WHERE processed = false AND dead_letter = false AND partition = ?
-- ORDER BY created_at; the full (processed, dead_letter, created_at) indexes
-- from V5/V7/V9 also cover the ever-growing processed half of the table.
-- A partial index only tracks unprocessed rows, so it stays small and the
-- poller scans a fraction of the index (direct CPU/I/O win per poll).
-- CONCURRENTLY is intentionally not used: Flyway runs migrations in a single
-- transaction and CREATE INDEX CONCURRENTLY cannot run inside one.
-- HIGH-4: index build on a large outbox exceeds the 30s per-role
-- statement_timeout. Lift it for this migration only. For very large tables
-- prefer the CONCURRENTLY operator script outside Flyway's transaction.
SET LOCAL statement_timeout = '10min';

CREATE INDEX IF NOT EXISTS idx_outbox_pending_partition_created
    ON outbox_events (partition, created_at)
    WHERE processed = false AND dead_letter = false;
