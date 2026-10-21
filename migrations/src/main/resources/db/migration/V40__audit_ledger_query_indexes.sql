-- V40__audit_ledger_query_indexes.sql
--
-- Query-surface indexes for the audit trail and the append-only ledger.
-- audit_logs already has (actor_user_id, timestamp, id) from V35; the
-- unfiltered admin listing (findAllByOrderByTimestampDescIdDesc) still sorts
-- the whole table, so add a matching (timestamp DESC, id DESC) index for an
-- index-only range scan with no sort. ledger_entries grows without any query
-- today; the (account_id, business_at DESC, id DESC) composite serves the
-- inevitable per-account reconciliation listing the same way.

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp_id
    ON audit_logs (timestamp DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_business
    ON ledger_entries (account_id, business_at DESC, id DESC);
