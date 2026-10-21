-- V43__transfer_history_keyset_covering_indexes.sql
--
-- DB-2/Perf-3: keyset pagination predicates need covering indexes on the
-- ORDER BY columns (created_at, id), not only on business_created_at.
-- The new findHistoryBetweenKeyset query filters
--   (sender = :id OR receiver = :id) AND business_created_at BETWEEN ...
--   AND (created_at, id) < (cursor) ORDER BY created_at DESC, id DESC
-- BitmapOr over the two legs still sorts, but the sort now feeds from
-- narrower index scans that include created_at for ordering.
-- Online-safe: plain CREATE INDEX (short ACCESS EXCLUSIVE, no rewrite).
-- HIGH-4: lift the 30s per-role statement_timeout for this build. For very
-- large tables prefer the CONCURRENTLY operator script outside Flyway.
SET LOCAL statement_timeout = '10min';

CREATE INDEX IF NOT EXISTS idx_transfers_sender_created_id
    ON transfers (sender_account_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_receiver_created_id
    ON transfers (receiver_account_id, created_at DESC, id DESC);
