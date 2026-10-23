-- V62: audit trail append-only guard.
--
-- The application role could previously UPDATE or DELETE audit_logs through
-- the ORM/repository, so a bug or a compromised application path could erase
-- evidence without leaving a trace. PostgreSQL row triggers now reject every
-- UPDATE and every DELETE, except the retention function below, which opts
-- into a transaction-local maintenance flag.
--
-- This is defense in depth, not full WORM: the application role still owns
-- the table and can disable the trigger or call the retention function with a
-- future cutoff. True WORM requires a separate maintenance role with DELETE
-- only on an archive path; that is an operational decision documented in the
-- audit report. TRUNCATE is blocked separately because row triggers do not
-- fire for it.

CREATE OR REPLACE FUNCTION audit_logs_reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_logs rows are immutable; UPDATE is forbidden';
    END IF;
    IF current_setting('app.audit_maintenance', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'audit_logs rows may only be deleted by the retention job';
    END IF;
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_logs_append_only ON audit_logs;
CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION audit_logs_reject_mutation();

CREATE OR REPLACE FUNCTION audit_logs_reject_truncate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs must not be truncated';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_logs_no_truncate ON audit_logs;
CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT
    EXECUTE FUNCTION audit_logs_reject_truncate();

-- The only supported DELETE path. The flag is transaction-local (`true`), so
-- it cannot leak into another transaction on a pooled connection.
CREATE OR REPLACE FUNCTION delete_audit_logs_before(p_cutoff TIMESTAMP, p_batch INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted INTEGER;
BEGIN
    IF p_cutoff IS NULL THEN
        RAISE EXCEPTION 'audit retention cutoff must not be null';
    END IF;
    IF p_batch IS NULL OR p_batch < 1 OR p_batch > 10000 THEN
        RAISE EXCEPTION 'audit retention batch must be between 1 and 10000';
    END IF;

    PERFORM set_config('app.audit_maintenance', 'on', true);

    WITH victims AS (
        SELECT id
        FROM audit_logs
        WHERE timestamp < p_cutoff
        ORDER BY timestamp, id
        LIMIT p_batch
    )
    DELETE FROM audit_logs AS a
    USING victims
    WHERE a.id = victims.id;

    GET DIAGNOSTICS deleted = ROW_COUNT;
    RETURN deleted;
END;
$$;
