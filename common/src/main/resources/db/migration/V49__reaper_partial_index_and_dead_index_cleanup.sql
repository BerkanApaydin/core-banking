-- V49__reaper_partial_index_and_dead_index_cleanup.sql
--
-- Owner: transfer
-- Reason: crash-window reaper scan needs a partial PENDING index; the bare
-- business_created_at index it replaces is dead (no query can use it).
--
-- PERF-2: the crash-window reaper (TransferPendingReaper) scans
--   WHERE status = 'PENDING' AND business_created_at < :cutoff
--   ORDER BY business_created_at ASC, id ASC
-- every 5 minutes. The nearest existing index
-- idx_transfers_status_created (status, created_at DESC) leads on status but
-- ranges/sorts on business_created_at, which it does not contain — so the
-- pathological case the reaper exists for (PENDING pile-up) degrades
-- linearly. A small partial index fixes it: it only tracks PENDING rows.
--
-- PERF-5 (partial): idx_transfers_business_created_at (V21, bare
-- business_created_at) is fully dead — every report/history query constrains
-- sender_account_id/receiver_account_id first (TransferJpaRepository), so no
-- plan can use the bare-column index. Drop it to stop paying write
-- amplification on every transfer insert. Remaining PERF-5 candidates stay
-- under review until pg_stat_user_indexes proves idx_scan == 0 in staging.
--
-- HIGH-4: lift the 30s per-role statement_timeout for this build.
SET LOCAL statement_timeout = '10min';

CREATE INDEX IF NOT EXISTS idx_transfers_pending_business_created
    ON transfers (status, business_created_at ASC, id ASC)
    WHERE status = 'PENDING';

DROP INDEX IF EXISTS idx_transfers_business_created_at;
