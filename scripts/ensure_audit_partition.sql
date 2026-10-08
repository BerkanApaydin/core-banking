-- scripts/ensure_audit_partition.sql
--
-- QUARTERLY runbook job (post-cutover only): pre-creates the current + next
-- half-year audit_logs partitions so inserts never fall into audit_logs_default.
-- Idempotent: existing partitions are skipped; no-ops (NOTICE) when the
-- cutover has not run yet.
--
-- HOW TO RUN (psql, any low-traffic moment; short ACCESS EXCLUSIVE on the
-- parent only, no data movement):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/ensure_audit_partition.sql
--
-- Cadence: diary entry every quarter (see docs/decisions/partitioning.md).
-- The AuditDefaultPartition alert (prometheus-rules.yaml) pages when rows
-- accumulate in audit_logs_default, i.e. this job was missed.

DO $$
DECLARE
    parent_kind CHAR;
    cur_half_start DATE;
    w_start DATE;
    w_end DATE;
    part_name TEXT;
    i INT;
BEGIN
    SELECT relkind INTO parent_kind FROM pg_class WHERE oid = to_regclass('public.audit_logs');
    IF parent_kind IS NULL OR parent_kind <> 'p' THEN
        RAISE NOTICE 'audit_logs is not partitioned yet (cutover pending): nothing to do';
        RETURN;
    END IF;

    cur_half_start := CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 6
        THEN make_date(EXTRACT(YEAR FROM CURRENT_DATE)::INT, 1, 1)
        ELSE make_date(EXTRACT(YEAR FROM CURRENT_DATE)::INT, 7, 1) END;

    -- Current half + next half: half-year alignment matches the cutover
    -- naming (pYYYYh1/h2), so windows never overlap existing partitions.
    FOR i IN 0..1 LOOP
        w_start := ((i * 6) || ' months')::INTERVAL + cur_half_start;
        w_start := w_start::DATE;
        w_end := (w_start + INTERVAL '6 months')::DATE;
        part_name := 'audit_logs_p' || to_char(w_start, 'YYYY') || 'h'
            || CASE WHEN EXTRACT(MONTH FROM w_start) = 1 THEN '1' ELSE '2' END;

        IF to_regclass('public.' || part_name) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                part_name, w_start, w_end);
            RAISE NOTICE 'created partition % for [% %)', part_name, w_start, w_end;
        ELSE
            RAISE NOTICE 'partition % already exists: nothing to do', part_name;
        END IF;
    END LOOP;
END $$;
