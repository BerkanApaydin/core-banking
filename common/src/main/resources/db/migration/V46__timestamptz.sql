-- V46__timestamptz.sql
--
-- Promote every wall-clock column from TIMESTAMP (without time zone) to
-- TIMESTAMPTZ. All values are UTC instants by construction (JVM pinned to UTC
-- via Dockerfile TZ + -Duser.timezone=UTC, time-strategy ADR bans bare now()
-- and systemDefaultZone in main code, enforced by CodingRulesArchitectureTest),
-- so USING ... AT TIME ZONE 'UTC' is a representation change, not a data
-- migration: no instant moves.
--
-- Why now (O-1): a single replica with a non-UTC host zone would otherwise
-- silently shift every stored instant (TIMESTAMP has no zone to convert
-- from). token_revocations.expires_at (V26) already runs TIMESTAMPTZ in
-- production; this extends the same guarantee to the money-movement tables.
-- Hibernate maps LocalDateTime <-> timestamptz transparently (driver converts
-- in the JVM zone, which is pinned UTC), so no Java change is needed.
--
-- Safe online on small tables (brief ACCESS EXCLUSIVE per ALTER, same pattern
-- as V32): each statement rewrites its table, so assess lock time in staging
-- before rolling against large transfers/ledger/audit tables. The per-role
-- statement_timeout (30s, see docker-init/init-db.sql) is lifted for this
-- migration only; long rewrites must not be killed mid-ALTER.
-- Flyway runs each migration in one transaction: SET LOCAL applies to it.

SET LOCAL statement_timeout = '10min';

-- V44's transfer_daily_totals matview selects transfers.business_created_at:
-- ALTER COLUMN TYPE refuses columns used by a view/rule. Drop + recreate
-- around the transfers rewrite (content is a pure re-derivation of transfers;
-- no application code reads the view yet, and the unique index is recreated
-- with it, so the rebuild is behavior-preserving).
DROP MATERIALIZED VIEW IF EXISTS transfer_daily_totals;

ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE users ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE accounts ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE accounts ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE transfers ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE transfers ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE transfers ALTER COLUMN business_created_at TYPE TIMESTAMPTZ USING business_created_at AT TIME ZONE 'UTC';

CREATE MATERIALIZED VIEW transfer_daily_totals AS
SELECT
    LEAST(sender_account_id, receiver_account_id) AS account_a,
    GREATEST(sender_account_id, receiver_account_id) AS account_b,
    sender_account_id AS sender_account_id,
    receiver_account_id AS receiver_account_id,
    date_trunc('day', business_created_at)::date AS day,
    COUNT(*) AS transfer_count,
    SUM(amount) AS total_volume
FROM transfers
GROUP BY sender_account_id, receiver_account_id, date_trunc('day', business_created_at);

CREATE UNIQUE INDEX idx_transfer_daily_totals_uniq
    ON transfer_daily_totals (sender_account_id, receiver_account_id, day);

ALTER TABLE audit_logs ALTER COLUMN "timestamp" TYPE TIMESTAMPTZ USING "timestamp" AT TIME ZONE 'UTC';

ALTER TABLE outbox_events ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE outbox_events ALTER COLUMN processed_at TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC';

ALTER TABLE idempotency_keys ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE ledger_entries ALTER COLUMN business_at TYPE TIMESTAMPTZ USING business_at AT TIME ZONE 'UTC';
ALTER TABLE ledger_entries ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE ledger_entries ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE refresh_tokens ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';
ALTER TABLE refresh_tokens ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE refresh_tokens ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- token_revocations.expires_at is already TIMESTAMPTZ since V26: nothing to do.
--
-- JDBC caveat (verified against pgjdbc 42.7.12): ResultSet.getObject(col,
-- LocalDateTime.class) REFUSES timestamptz columns ("Cannot convert the column
-- of type TIMESTAMPTZ to requested type java.time.LocalDateTime"). Raw-JDBC
-- readers must take OffsetDateTime and convert (see BacklogMetricsReporter).
-- JPA/Hibernate mappings are unaffected (LocalDateTime works for both types).
