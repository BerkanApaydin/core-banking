-- V66: enforce double-entry balance per transaction_ref at commit.
--
-- V29 documents the invariant and LedgerReconciliationJob detects violations
-- after the fact. This deferred constraint trigger makes PostgreSQL reject a
-- commit whose transaction_ref legs do not net to zero, so an application bug
-- cannot persist an unbalanced journal entry even if one leg is accidentally
-- omitted. The check is deferred because both legs are written in the same
-- business transaction and only need to balance at commit time.
--
-- UPDATE/DELETE are already blocked by V65, so INSERT is the only mutation
-- that can create a new ref (or append to an existing one).

CREATE OR REPLACE FUNCTION ledger_entries_require_balanced_ref()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    net_amount NUMERIC(38,2);
BEGIN
    SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
      INTO net_amount
      FROM ledger_entries
     WHERE transaction_ref = NEW.transaction_ref;

    IF net_amount <> 0 THEN
        RAISE EXCEPTION 'ledger transaction_ref % does not net to zero (net=%)',
            NEW.transaction_ref, net_amount
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_ledger_entries_balanced_ref ON ledger_entries;
CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced_ref
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION ledger_entries_require_balanced_ref();
