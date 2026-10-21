-- V36__backfill_and_lock_optimistic_versions.sql
--
-- Closes the nullable-@Version window left by V1 (accounts.version, never
-- constrained) and V8 (transfers.version DROP NOT NULL, "managed by
-- Hibernate"). Hibernate initializes @Version to 0 on persist, so every row
-- written through the application already carries 0 — but rows inserted by raw
-- SQL (seeds, repairs, pre-Hibernate history) may hold NULL. A NULL version
-- can never match a detached domain snapshot, so such rows fail every guarded
-- update with a deterministic optimistic-lock conflict (see
-- AccountPersistenceAdapter/TransferPersistenceAdapter/UserPersistenceAdapter
-- null guards, pinned by AggregateVersioningIntegrationTest).
--
-- This migration backfills those rows once, then locks the columns the way
-- V12 already did for users.version: DEFAULT 0 + NOT NULL. JPA mappings carry
-- @Column(nullable = false) to match, so ddl-auto=validate fails fast on any
-- future drift instead of discovering it on a legacy-row write.
-- Do NOT "fix" this by editing V1/V8: Flyway checksums protect applied history.
-- HIGH-4: full-table UPDATEs exceed the 30s per-role statement_timeout on a
-- populated DB. Lift it for this migration only.
SET LOCAL statement_timeout = '10min';

UPDATE accounts SET version = 0 WHERE version IS NULL;
UPDATE transfers SET version = 0 WHERE version IS NULL;

ALTER TABLE accounts ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE accounts ALTER COLUMN version SET NOT NULL;
ALTER TABLE transfers ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE transfers ALTER COLUMN version SET NOT NULL;
