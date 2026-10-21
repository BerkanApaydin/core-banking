-- V61__transfer_report_business_keyset_indexes.sql
--
-- The report keyset cursor is Transfer.createdAt (business time, exposed as
-- createdAt and stored in business_created_at). The keyset predicate and the
-- page ordering must therefore use the same business-time column:
--   (business_created_at, id) < (cursor)
--   ORDER BY business_created_at DESC, id DESC
--
-- V43's (account_id, created_at DESC, id DESC) indexes serve the history
-- endpoint (insert order) but cannot serve this business-time range order.
-- Add per-side covering indexes and drop the V28 prefix indexes they subsume
-- (the new indexes have the same leading columns plus the id tiebreaker).
--
-- Online-safe: plain CREATE INDEX takes only a brief ACCESS EXCLUSIVE lock
-- and does no table rewrite. HIGH-4: lift the 30s statement_timeout for the
-- builds. For very large tables prefer the CONCURRENTLY operator script
-- outside Flyway.
SET LOCAL statement_timeout = '10min';

CREATE INDEX IF NOT EXISTS idx_transfers_sender_business_created_id
    ON transfers (sender_account_id, business_created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_receiver_business_created_id
    ON transfers (receiver_account_id, business_created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_transfers_sender_business_created;
DROP INDEX IF EXISTS idx_transfers_receiver_business_created;
