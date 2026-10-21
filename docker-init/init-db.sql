-- Test/support database only. Flyway owns the schema of bank_db (V1..V60
-- migrations run on startup); nothing here may create tables or roles.
-- Testcontainers-based tests do NOT use this database at all.
-- Create test database if it doesn't exist
CREATE DATABASE bank_db_test;

-- 8.3: database-level backstop behind the 30s application transaction timeout.
-- A single statement can never outlive the transaction budget (the DB kills it
-- first, so Hikari never waits on a dead statement), and a leaked idle
-- transaction is reaped at 2x the budget. Long one-off migrations (backfills,
-- VALIDATE CONSTRAINT on large tables) must override per-session instead:
--   SET LOCAL statement_timeout = '10min';
-- before the heavy statement.
ALTER ROLE bank_user SET statement_timeout = '30s';
ALTER ROLE bank_user SET idle_in_transaction_session_timeout = '60s';

-- UTC-only timestamp contract (docs/decisions/time-strategy.md). V46 turned
-- every wall-clock column into TIMESTAMPTZ, but the native SQL binds
-- LocalDateTime as a zone-less PG `timestamp`, and PostgreSQL converts
-- timestamp -> timestamptz using the SESSION time zone. Pinning the role's
-- zone makes the conversion independent of the server's local time zone.
-- The application additionally pins the session through the JDBC URL
-- (options=-c TimeZone=UTC), so a managed instance that ignores the role
-- default still behaves.
ALTER ROLE bank_user SET timezone = 'UTC';
ALTER DATABASE bank_db SET timezone = 'UTC';
