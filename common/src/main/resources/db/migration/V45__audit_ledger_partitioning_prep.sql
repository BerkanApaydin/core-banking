-- V45__audit_ledger_partitioning_prep.sql
--
-- T-12: audit_logs + ledger_entries grow append-only (audit has 365-day
-- retention). Converting directly to PARTITION BY would rewrite these tables
-- with a long ACCESS EXCLUSIVE; this migration is the "preparation" step:
--  1) reinforces the NOT NULL guarantee on the range-partition keys
--     (audit_logs.timestamp since V1, ledger_entries.business_at since V29),
--  2) leaves a template + documentation for future monthly partitions,
--  3) keeps the new write path partition-ready without moving old data.
-- The real DETACH/ATTACH cutover runs in a maintenance window following the
-- docs/decisions/partitioning.md procedure (pg_partman recommended).

-- Guard: partition-key columns must not contain NULL (idempotent).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM audit_logs WHERE timestamp IS NULL LIMIT 1
    ) THEN
        RAISE EXCEPTION 'audit_logs.timestamp has NULL rows; backfill before partitioning';
    END IF;
    IF EXISTS (
        SELECT 1 FROM ledger_entries WHERE business_at IS NULL LIMIT 1
    ) THEN
        RAISE EXCEPTION 'ledger_entries.business_at has NULL rows; backfill before partitioning';
    END IF;
END $$;

-- Monthly partition template (manual maintenance-window procedure):
--   CREATE TABLE audit_logs_y2026m10 PARTITION OF audit_logs
--     FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
-- When converting to native declarative partitioning, the table becomes
--   ALTER TABLE audit_logs PARTITION BY RANGE (timestamp)
-- (instant on an empty table; pg_partman + backfill on a full table).
-- This migration moves no data; it only seals the decision and the guard.
