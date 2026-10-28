-- V65: ledger_entries append-only guard.
--
-- V29 documented the journal as append-only, but enforcement lived only in
-- the application: a bug or a direct database session could UPDATE or DELETE
-- a posted leg and leave the double-entry evidence inconsistent with the
-- operational balance. PostgreSQL row triggers now reject every UPDATE and
-- every DELETE; only a transaction-local maintenance flag (used by test
-- fixtures and an eventual archive/retention path) may delete rows.
--
-- This is defense in depth, not full WORM: the application role still owns
-- the table and can disable the trigger. Production should additionally run
-- the application as a role with INSERT/SELECT only, as described in the
-- audit roadmap. TRUNCATE is blocked separately because row triggers do not
-- fire for it.

CREATE OR REPLACE FUNCTION ledger_entries_reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'ledger_entries rows are immutable; UPDATE is forbidden';
    END IF;
    IF current_setting('app.ledger_maintenance', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'ledger_entries rows are append-only; DELETE requires the maintenance flag';
    END IF;
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS trg_ledger_entries_append_only ON ledger_entries;
CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION ledger_entries_reject_mutation();

CREATE OR REPLACE FUNCTION ledger_entries_reject_truncate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries must not be truncated';
END;
$$;

DROP TRIGGER IF EXISTS trg_ledger_entries_no_truncate ON ledger_entries;
CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT
    EXECUTE FUNCTION ledger_entries_reject_truncate();
