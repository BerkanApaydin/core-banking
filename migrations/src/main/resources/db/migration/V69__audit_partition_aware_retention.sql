-- V69: partition-aware audit retention.
--
-- V67's delete_audit_logs_before() deletes only the oldest hash-chain prefix,
-- and after the audit partition cutover the oldest rows live in whole
-- half-year partitions. A cross-partition DELETE still rewrites and vacuums
-- those tables; DROP TABLE is metadata-only and instant. This migration
-- teaches the retention function to drop partitions whose entire range is
-- older than the cutoff, but only when the dropped rows are a true chain
-- prefix: a backdated row inserted later can carry a high chain_seq inside an
-- old timestamp partition, so dropping that partition would remove a
-- high-sequence row while lower-sequence rows survive, leaving a hole the
-- verifier reports as tampering. In that case the function falls back to the
-- V67 prefix DELETE and waits for the newer rows to expire.
--
-- The non-partitioned path is unchanged. V68's SECURITY DEFINER, pinned
-- search_path, 30-day cutoff floor and grants are preserved by CREATE OR
-- REPLACE (the function keeps its OID and ACL).
--
-- Operational note: partition drops are metadata operations and are not
-- bounded by p_batch. The row count they remove is still added to the return
-- value so the scheduler log remains truthful.

CREATE OR REPLACE FUNCTION delete_audit_logs_before(p_cutoff TIMESTAMP, p_batch INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    deleted INTEGER := 0;
    v_rows INTEGER;
    v_prefix_end BIGINT;
    v_parent_kind "char";
    v_part RECORD;
    v_bound TEXT;
    v_upper_text TEXT;
    v_upper TIMESTAMPTZ;
    v_candidate_names TEXT[] := ARRAY[]::TEXT[];
    v_candidate_oids OID[] := ARRAY[]::OID[];
    v_candidate_rows BIGINT := 0;
    v_max_candidate_seq BIGINT := 0;
    v_min_retained_seq BIGINT;
    v_partition_name TEXT;
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
    -- A partition drop takes ACCESS EXCLUSIVE on the parent; fail fast and let
    -- the next scheduled run retry instead of queueing audit inserts behind a
    -- stuck DDL for the whole scheduler budget.
    PERFORM set_config('lock_timeout', '10s', true);

    SELECT relkind
      INTO v_parent_kind
      FROM pg_class
     WHERE oid = to_regclass('public.audit_logs');

    IF v_parent_kind = 'p' THEN
        FOR v_part IN
            SELECT c.oid, c.relname,
                   pg_get_expr(c.relpartbound, c.oid) AS bound
              FROM pg_inherits i
              JOIN pg_class c ON c.oid = i.inhrelid
             WHERE i.inhparent = to_regclass('public.audit_logs')
        LOOP
            v_bound := v_part.bound;
            -- Matches FOR VALUES FROM ('...') TO ('...'). DEFAULT, MINVALUE
            -- and MAXVALUE bounds do not contain a quoted upper bound and are
            -- deliberately skipped: they are never a closed, fully-expired
            -- range.
            v_upper_text := substring(v_bound from 'TO \(''([^'']+)''\)');
            IF v_upper_text IS NULL THEN
                CONTINUE;
            END IF;
            v_upper := v_upper_text::TIMESTAMPTZ;
            IF v_upper <= (p_cutoff AT TIME ZONE 'UTC') THEN
                v_candidate_names := v_candidate_names || v_part.relname;
                v_candidate_oids := v_candidate_oids || v_part.oid;
            END IF;
        END LOOP;

        IF array_length(v_candidate_oids, 1) IS NOT NULL THEN
            SELECT COALESCE(MAX(chain_seq), 0)
              INTO v_max_candidate_seq
              FROM public.audit_logs
             WHERE tableoid = ANY (v_candidate_oids);

            SELECT MIN(chain_seq)
              INTO v_min_retained_seq
              FROM public.audit_logs
             WHERE tableoid <> ALL (v_candidate_oids);

            -- Chain-prefix guard: all candidate rows must sort before every
            -- retained row. A backdated row in an old partition has a high
            -- chain_seq, so v_max_candidate_seq >= v_min_retained_seq and the
            -- function keeps the partition, falling back to prefix DELETE.
            IF v_min_retained_seq IS NULL OR v_max_candidate_seq < v_min_retained_seq THEN
                SELECT count(*)
                  INTO v_candidate_rows
                  FROM public.audit_logs
                 WHERE tableoid = ANY (v_candidate_oids);

                FOREACH v_partition_name IN ARRAY v_candidate_names LOOP
                    EXECUTE format('DROP TABLE public.%I', v_partition_name);
                END LOOP;
                deleted := v_candidate_rows;
            END IF;
        END IF;
    END IF;

    -- Boundary/current partition (or the non-partitioned table): delete only
    -- the oldest chain prefix, exactly as V67. After a partition drop this
    -- removes rows older than the cutoff from the partition that straddles
    -- the boundary.
    SELECT COALESCE(
             (SELECT MIN(chain_seq)
                FROM public.audit_logs
               WHERE "timestamp" >= (p_cutoff AT TIME ZONE 'UTC')),
             (SELECT MAX(chain_seq) + 1 FROM public.audit_logs),
             1)
      INTO v_prefix_end;

    WITH victims AS (
        SELECT id
          FROM public.audit_logs
         WHERE chain_seq < v_prefix_end
         ORDER BY chain_seq
         LIMIT p_batch
    )
    DELETE FROM public.audit_logs AS a
     USING victims
     WHERE a.id = victims.id;

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RETURN deleted + v_rows;
END;
$$;
