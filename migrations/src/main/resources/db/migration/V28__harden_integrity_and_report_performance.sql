-- V28__harden_integrity_and_report_performance.sql
--
-- 1) business_created_at is now the single business-time source for transfer
--    reports (TransferJpaRepository.findHistoryBetween). V20 backfilled it, but
--    the column stayed nullable and the query kept a COALESCE fallback that
--    defeats the V21 business-time btree index (a function over the column
--    cannot use a plain range scan). Backfill once more for safety, then
--    enforce NOT NULL so the bare-column predicate is always correct.
-- 2) OR-history queries (sender = ? OR receiver = ?) need per-side composite
--    indexes so each branch can range-scan business time without sorting.
-- 3) Enum-backed columns get CHECK constraints so the database — not just the
--    Java enums — rejects corrupt or out-of-band writes.
-- 4) idx_users_email (V18) never served a query (no findByEmail exists); drop
--    it to stop paying write amplification on every user insert/update.
--
-- HIGH-4: full-table UPDATE + index builds exceed the 30s per-role
-- statement_timeout on a populated DB. Lift it for this migration only.
-- NOTE: plain CREATE INDEX takes a write-blocking lock; for large tables
-- prefer the CONCURRENTLY operator script (scripts/create_index_concurrently.sql)
-- run outside Flyway's transaction instead.
SET LOCAL statement_timeout = '10min';

UPDATE transfers SET business_created_at = created_at WHERE business_created_at IS NULL;

ALTER TABLE transfers ALTER COLUMN business_created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transfers_sender_business_created
    ON transfers (sender_account_id, business_created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_receiver_business_created
    ON transfers (receiver_account_id, business_created_at DESC);

ALTER TABLE accounts ADD CONSTRAINT chk_accounts_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));

ALTER TABLE accounts ADD CONSTRAINT chk_accounts_currency
    CHECK (currency IN ('TRY', 'USD', 'EUR'));

ALTER TABLE transfers ADD CONSTRAINT chk_transfers_currency
    CHECK (currency IN ('TRY', 'USD', 'EUR'));
-- NOTE: chk_transfers_status already exists (V6/V8); not re-added here.

ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));

DROP INDEX IF EXISTS idx_users_email;
