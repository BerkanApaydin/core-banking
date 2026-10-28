-- V67: tamper-evident audit hash chain.
--
-- V62 made audit_logs append-only, but a privileged/application session could
-- still DELETE a row (with the maintenance flag) or rewrite a row after
-- disabling the trigger without leaving an internal trace. This migration
-- links every audit row to its predecessor and adds a verification function
-- used by the scheduled chain check:
--
--   row_hash  = sha256(canonical row fields || prev_hash)
--   prev_hash = row_hash of the previously appended audit row
--
-- The chain order is serialized by the audit_chain_state row lock, so two
-- concurrent business transactions cannot fork the chain. Retention keeps the
-- prefix invariant: delete_audit_logs_before now removes only a chain prefix
-- (oldest rows first), which is exactly what a verifier can tolerate.
--
-- Limits (documented in docs/decisions/audit-integrity.md): an actor with
-- enough privileges can rewrite the whole chain, and deleting the newest
-- suffix can only be detected while audit_chain_state still points past the
-- deletion. Full WORM still requires separate roles and an external anchor.

SET LOCAL statement_timeout = '10min';
SET LOCAL lock_timeout = '30s';

ALTER TABLE audit_logs
    ADD COLUMN chain_seq BIGINT,
    ADD COLUMN prev_hash VARCHAR(64),
    ADD COLUMN row_hash VARCHAR(64);

CREATE TABLE audit_chain_state (
    id SMALLINT PRIMARY KEY,
    last_seq BIGINT NOT NULL,
    last_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_audit_chain_state_singleton CHECK (id = 1)
);

INSERT INTO audit_chain_state (id, last_seq, last_hash) VALUES (1, 0, '');

CREATE OR REPLACE FUNCTION audit_logs_row_hash(
    p_username TEXT,
    p_action TEXT,
    p_details TEXT,
    p_timestamp TIMESTAMPTZ,
    p_actor_user_id BIGINT,
    p_prev_hash TEXT
)
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
    SELECT encode(sha256(convert_to(
        coalesce(p_username, '') || E'\x1f'
        || coalesce(p_action, '') || E'\x1f'
        || coalesce(p_details, '') || E'\x1f'
        || to_char(p_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US') || E'\x1f'
        || coalesce(p_actor_user_id::TEXT, '') || E'\x1f'
        || coalesce(p_prev_hash, '')
    , 'UTF8')), 'hex');
$$;

-- Backfill in insertion order (id order on a non-partitioned table). The
-- append-only trigger blocks UPDATE, so it is disabled for this migration
-- only; Flyway's transaction re-enables it even if the backfill fails.
ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_append_only;

DO $$
DECLARE
    r RECORD;
    v_seq BIGINT := 0;
    v_prev TEXT := '';
    v_hash TEXT;
BEGIN
    FOR r IN
        SELECT id, username, action::TEXT AS action, details, "timestamp", actor_user_id
          FROM audit_logs
         ORDER BY id
    LOOP
        v_seq := v_seq + 1;
        v_hash := audit_logs_row_hash(r.username, r.action, r.details,
                                      r."timestamp", r.actor_user_id, v_prev);
        UPDATE audit_logs
           SET chain_seq = v_seq,
               prev_hash = v_prev,
               row_hash = v_hash
         WHERE id = r.id;
        v_prev := v_hash;
    END LOOP;

    UPDATE audit_chain_state
       SET last_seq = v_seq,
           last_hash = v_prev
     WHERE id = 1;
END $$;

ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_logs_append_only;

ALTER TABLE audit_logs ALTER COLUMN chain_seq SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN prev_hash SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN row_hash SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_logs_chain_seq ON audit_logs (chain_seq);

CREATE OR REPLACE FUNCTION audit_logs_assign_chain()
RETURNS trigger
LANGUAGE plpgsql
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

DROP TRIGGER IF EXISTS trg_audit_logs_chain ON audit_logs;
CREATE TRIGGER trg_audit_logs_chain
    BEFORE INSERT ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION audit_logs_assign_chain();

-- Retention deletes only the oldest chain prefix: rows before the first row
-- whose event timestamp is still inside the window. A backdated row that
-- follows a newer row waits for the next run (bounded retention delay) instead
-- of punching a hole in the chain and making every later row look tampered.
CREATE OR REPLACE FUNCTION delete_audit_logs_before(p_cutoff TIMESTAMP, p_batch INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted INTEGER;
    v_prefix_end BIGINT;
BEGIN
    IF p_cutoff IS NULL THEN
        RAISE EXCEPTION 'audit retention cutoff must not be null';
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

-- Returns the chain_seq of the first broken link, or NULL when the chain is
-- intact. The first retained row is skipped for the prev_hash comparison
-- because retention intentionally prunes its predecessor. audit_chain_state
-- catches deletion of the newest suffix while at least one row remains.
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

    SELECT chain_seq, row_hash
      INTO v_max_seq, v_max_hash
      FROM audit_logs
     ORDER BY chain_seq DESC
     LIMIT 1;

    IF v_max_seq IS NULL THEN
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
