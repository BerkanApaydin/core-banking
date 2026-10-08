-- scripts/audit_partition_finalize.sql
--
-- SECOND half of the maintenance-window cutover (see
-- scripts/audit_partition_cutover.sql + docs/decisions/partitioning.md).
-- Run AFTER post-swap reads are green (new rows landing in the current
-- partition, admin listing + per-actor lookup verified against audit_logs).
--
-- HOW TO RUN: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/audit_partition_finalize.sql
--
-- Does, atomically:
--   1) sanity-checks new rows >= legacy rows (post-swap writes only add),
--   2) drops the audit_logs_legacy copy (with its old indexes),
--   3) renames the _new parent indexes to their canonical names.
-- Afterwards switch retention to DROP PARTITION per partitioning.md step 4.

SET lock_timeout = '30s';

BEGIN;

DO $$
DECLARE
    new_rows BIGINT;
    legacy_rows BIGINT;
BEGIN
    IF to_regclass('public.audit_logs_legacy') IS NULL THEN
        RAISE EXCEPTION 'audit_logs_legacy missing: cutover did not run or finalize already ran';
    END IF;
    SELECT count(*) INTO new_rows FROM audit_logs;
    SELECT count(*) INTO legacy_rows FROM audit_logs_legacy;
    IF new_rows < legacy_rows THEN
        RAISE EXCEPTION 'new audit_logs (%) has fewer rows than legacy (%): investigate before finalizing',
            new_rows, legacy_rows;
    END IF;
    RAISE NOTICE 'finalizing: new=% legacy=% (delta = post-swap writes)', new_rows, legacy_rows;
END $$;

DROP TABLE audit_logs_legacy;

ALTER INDEX idx_audit_logs_timestamp_new RENAME TO idx_audit_logs_timestamp;
ALTER INDEX idx_audit_logs_username_new RENAME TO idx_audit_logs_username;
ALTER INDEX idx_audit_logs_timestamp_id_new RENAME TO idx_audit_logs_timestamp_id;
ALTER INDEX idx_audit_logs_actor_timestamp_new RENAME TO idx_audit_logs_actor_timestamp;

COMMIT;

ANALYZE audit_logs;
SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'audit_logs';
