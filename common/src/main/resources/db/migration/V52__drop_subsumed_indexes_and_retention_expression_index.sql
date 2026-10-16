-- Owner: shared
-- Reason: drop L2 subsumed/dead indexes (write amplification) + L3 outbox retention expression index
--
-- V52__drop_subsumed_indexes_and_retention_expression_index.sql
--
-- L2 (verified against the current query set; V49 protocol):
--   * idx_outbox_events_unprocessed (V5, (processed, created_at)) is never the
--     cheapest path: every live outbox query constrains
--     processed = false AND dead_letter = false
--     (OutboxJpaRepository.findAndLockUnprocessed/findByIdForUpdateSkipLocked/
--     countPendingOutsidePartitionRange) and is served by
--     idx_outbox_events_pending (V7, (processed, dead_letter, created_at))
--     or the V42 partial idx_outbox_pending_partition_created.
--   * idx_transfers_created_at (V2, ASC) vs idx_transfers_created_at_desc
--     (V21, DESC): a btree scans backwards, the DESC twin adds nothing.
--     Keep the DESC variant that matches ORDER BY created_at DESC.
--   * idx_audit_logs_timestamp (V3/V15, (timestamp DESC)) is a strict prefix of
--     idx_audit_logs_timestamp_id (V40, (timestamp DESC, id DESC)) which matches
--     the repository ordering exactly
--     (AuditLogJpaRepository.findAll*/findByTimestampBetween...OrderByTimestampDescIdDesc)
--     and the retention IN-subquery.
--   * idx_ledger_account_business (V29, (account_id, business_at DESC)) is a
--     strict prefix of idx_ledger_entries_account_business
--     (V40, (account_id, business_at DESC, id DESC)); no ledger read query
--     exists yet (LedgerEntryJpaRepository is write-only), so keep the longer
--     composite for the inevitable reconciliation listing.
--   * idx_accounts_user_id_status (V13, (user_id, status)): no query filters
--     user_id + status; the list path is findByUserIdOrderByCreatedAtDescIdDesc
--     served by the V17 (user_id, created_at DESC) index.
--   * idx_audit_logs_username (V3/V15) / idx_audit_logs_username_action (V13):
--     no username-based audit query exists (AuditLogJpaRepository is
--     timestamp/actor-only). The partition cutover scripts were aligned to not
--     resurrect these (see scripts/audit_partition_cutover.sql).
--   * idx_idempotency_keys_created_at (V16, bare created_at): cleanup/backlog
--     queries use the V38 partials
--     (idx_idempotency_cleanup_kind_created / idx_idempotency_pending_http_kind).
--
-- L3: outbox retention filters/orders on COALESCE(processed_at, created_at),
-- which no plain index can serve. Add a partial expression index scoped to the
-- retention predicate so each sweep seeks the processed tail instead of
-- scanning it. Legacy rows processed before processed_at was stamped are
-- covered via the same COALESCE expression.
--
-- HIGH-4: index builds on hot tables exceed the 30s per-role
-- statement_timeout on a populated DB. Lift it for this migration only.
-- For very large tables prefer the CONCURRENTLY operator script outside
-- Flyway's transaction (scripts/create_index_concurrently.sql).
SET LOCAL statement_timeout = '10min';

DROP INDEX IF EXISTS idx_outbox_events_unprocessed;
DROP INDEX IF EXISTS idx_transfers_created_at;
DROP INDEX IF EXISTS idx_audit_logs_timestamp;
DROP INDEX IF EXISTS idx_ledger_account_business;
DROP INDEX IF EXISTS idx_accounts_user_id_status;
DROP INDEX IF EXISTS idx_audit_logs_username;
DROP INDEX IF EXISTS idx_audit_logs_username_action;
DROP INDEX IF EXISTS idx_idempotency_keys_created_at;

CREATE INDEX IF NOT EXISTS idx_outbox_retention_processed_created
    ON outbox_events ((COALESCE(processed_at, created_at)), id)
    WHERE processed = true AND dead_letter = false;
