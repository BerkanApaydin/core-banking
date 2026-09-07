-- V22__drop_cross_bc_foreign_keys.sql
-- Implements the No-FK decision recorded (but not executed) in V19.
--
-- V3 created cross-bounded-context foreign keys (fk_accounts_user_id,
-- fk_transfers_sender_id, fk_transfers_receiver_id). Those constraints were
-- never dropped, so every migrated database still enforces cross-BC coupling:
-- aggregates cannot be written/deleted independently, per-BC storage splits
-- are blocked, and test/database cleanup order is dictated by the FK graph
-- (see CI failure: DELETE FROM accounts blocked by leftover transfers rows).
--
-- This migration drops ONLY the three FK constraints. Everything else from V3
-- stays: CHECK constraints (non-negative balance, positive amount, no
-- self-transfer, status values) and all indexes.
--
-- Orphan detection after the drop is covered by OrphanIntegrityReporter
-- (app.bootstrap, nightly, report-only).

ALTER TABLE transfers DROP CONSTRAINT IF EXISTS fk_transfers_sender_id;
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS fk_transfers_receiver_id;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS fk_accounts_user_id;
