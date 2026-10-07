-- Test/support database only. Flyway owns the schema of bank_db (V1..V34
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
