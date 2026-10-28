-- Owner: shared
-- Reason: least-privilege database roles for audit/ledger (F1/F2) and a
--         full-table-wipe signal in the audit chain verifier.
--
-- V62/V65 made audit_logs and ledger_entries append-only, and V67 linked every
-- audit row into a hash chain. Those guards are defense-in-depth only while
-- the runtime role cannot bypass them: a table owner can disable a trigger,
-- and a role with DELETE can call the retention function with a future cutoff.
-- This migration hardens the privileged entry points so the companion script
-- scripts/provision_least_privilege_roles.sql can run the application as
-- bank_app (INSERT/SELECT on the append-only tables, no UPDATE/DELETE):
--
--   1. audit_logs_assign_chain() runs SECURITY DEFINER, so the runtime role
--      never needs UPDATE on audit_chain_state; the search_path is pinned to
--      prevent function shadowing through a writable schema.
--   2. delete_audit_logs_before() runs SECURITY DEFINER (the runtime role
--      needs no DELETE on audit_logs) and rejects a cutoff newer than 30 days,
--      so a compromised runtime session cannot erase recent evidence.
--   3. audit_logs_first_broken_chain_seq() reports -1 for an empty table while
--      audit_chain_state still points at a non-zero head: a total table wipe
--      (or a full retention pass) is no longer indistinguishable from a fresh,
--      empty chain.
--
-- Limits (documented in docs/decisions/least-privilege-db-roles.md): the
-- migration owner can still rewrite the chain and the state row, and there is
-- no external anchor. Full WORM still requires KMS/HSM custody and an
-- off-database anchor; this migration closes the ordinary runtime-role path.

-- 1. Chain assignment as the function owner. The trigger is fired by
--    bank_app's INSERT but updates audit_chain_state with the owner's rights.
CREATE OR REPLACE FUNCTION audit_logs_assign_chain()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_seq BIGINT;
    v_prev TEXT;
BEGIN
    SELECT last_seq, last_hash
      INTO v_seq, v_prev
      FROM audit_chain_state
     WHERE id = 1
       FOR UPDATE;

    IF v_seq IS NULL THEN
        RAISE EXCEPTION 'audit_chain_state singleton row is missing';
    END IF;

    NEW.chain_seq := v_seq + 1;
    NEW.prev_hash := v_prev;
    NEW.row_hash := audit_logs_row_hash(NEW.username, NEW.action::TEXT, NEW.details,
                                        NEW."timestamp", NEW.actor_user_id, NEW.prev_hash);

    UPDATE audit_chain_state
       SET last_seq = NEW.chain_seq,
           last_hash = NEW.row_hash
     WHERE id = 1;

    RETURN NEW;
END;
$$;

-- 2. Retention is the only supported DELETE path. SECURITY DEFINER lets the
--    runtime role call it without DELETE on audit_logs; the 30-day floor
--    prevents a future cutoff from being used to erase recent evidence. The
--    existing transaction-local app.audit_maintenance flag remains the signal
--    the append-only trigger checks.
CREATE OR REPLACE FUNCTION delete_audit_logs_before(p_cutoff TIMESTAMP, p_batch INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    deleted INTEGER;
    v_prefix_end BIGINT;
BEGIN
    IF p_cutoff IS NULL THEN
        RAISE EXCEPTION 'audit retention cutoff must not be null';
    END IF;
    IF p_cutoff > (now() AT TIME ZONE 'UTC') - INTERVAL '30 days' THEN
        RAISE EXCEPTION 'audit retention cutoff must be at least 30 days old (got %)', p_cutoff;
    END IF;
    IF p_batch IS NULL OR p_batch < 1 OR p_batch > 10000 THEN
        RAISE EXCEPTION 'audit retention batch must be between 1 and 10000';
    END IF;

    PERFORM set_config('app.audit_maintenance', 'on', true);

    SELECT COALESCE(
             (SELECT MIN(chain_seq)
                FROM audit_logs
               WHERE "timestamp" >= (p_cutoff AT TIME ZONE 'UTC')),
             (SELECT MAX(chain_seq) + 1 FROM audit_logs),
             1)
      INTO v_prefix_end;

    WITH victims AS (
        SELECT id
          FROM audit_logs
         WHERE chain_seq < v_prefix_end
         ORDER BY chain_seq
         LIMIT p_batch
    )
    DELETE FROM audit_logs AS a
     USING victims
     WHERE a.id = victims.id;

    GET DIAGNOSTICS deleted = ROW_COUNT;
    RETURN deleted;
END;
$$;

-- 3. Empty-table signal. A fresh database has state last_seq = 0; a table with
--    no rows but a non-zero head means the retained evidence was removed.
CREATE OR REPLACE FUNCTION audit_logs_first_broken_chain_seq()
RETURNS BIGINT
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_state_seq BIGINT;
    v_state_hash TEXT;
    v_max_seq BIGINT;
    v_max_hash TEXT;
    v_broken BIGINT;
BEGIN
    SELECT last_seq, last_hash
      INTO v_state_seq, v_state_hash
      FROM audit_chain_state
     WHERE id = 1;

    IF v_state_seq IS NULL THEN
        -- The state singleton is part of the evidence; losing it is broken.
        RETURN -1;
    END IF;

    SELECT chain_seq, row_hash
      INTO v_max_seq, v_max_hash
      FROM audit_logs
     ORDER BY chain_seq DESC
     LIMIT 1;

    IF v_max_seq IS NULL THEN
        IF v_state_seq > 0 THEN
            -- Empty table with a non-zero head: full wipe or full retention.
            RETURN -1;
        END IF;
        RETURN NULL;
    END IF;

    IF v_max_seq <> v_state_seq OR v_max_hash IS DISTINCT FROM v_state_hash THEN
        RETURN v_max_seq;
    END IF;

    WITH chain AS (
        SELECT a.chain_seq,
               a.row_hash,
               a.prev_hash,
               lag(a.row_hash) OVER (ORDER BY a.chain_seq) AS previous_row_hash,
               audit_logs_row_hash(a.username, a.action::TEXT, a.details,
                                   a."timestamp", a.actor_user_id, a.prev_hash)
                   AS expected_row_hash
          FROM audit_logs a
    )
    SELECT chain_seq
      INTO v_broken
      FROM chain
     WHERE row_hash IS DISTINCT FROM expected_row_hash
        OR (previous_row_hash IS NOT NULL
            AND prev_hash IS DISTINCT FROM previous_row_hash)
     ORDER BY chain_seq
     LIMIT 1;

    RETURN v_broken;
END;
$$;

-- 4. The retention entry point is not PUBLIC. The owner keeps EXECUTE
--    implicitly; bank_app and the maintenance role are granted explicitly
--    here when they already exist, and again by the provisioning script.
REVOKE ALL ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_app') THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER) TO bank_app';
        EXECUTE 'GRANT EXECUTE ON FUNCTION audit_logs_assign_chain() TO bank_app';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_audit_maintenance') THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER) TO bank_audit_maintenance';
    END IF;
END $$;
