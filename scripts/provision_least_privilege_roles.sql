-- scripts/provision_least_privilege_roles.sql
--
-- F1/F2 operational hardening: separate the schema owner (Flyway) from the
-- runtime role, and give the runtime role no direct DML on the append-only
-- evidence tables.
--
-- Run as the schema owner (the Flyway user) after migrations:
--
--   psql "$OWNER_DATABASE_URL" -v ON_ERROR_STOP=1 \
--        -f scripts/provision_least_privilege_roles.sql
--
-- Then set the runtime password out of band (never commit it):
--
--   ALTER ROLE bank_app PASSWORD '<from-secret-manager>';
--
-- and deploy the application with:
--
--   DB_USERNAME=bank_app          DB_PASSWORD=<app secret>
--   FLYWAY_USER=<owner>           FLYWAY_PASSWORD=<owner secret>
--
-- Optionally point the reconciliation scheduler at its own role:
--
--   SCHEDULER_DB_URL=jdbc:postgresql://<host>:5432/bank_db
--   SCHEDULER_DB_USERNAME=bank_scheduler
--   SCHEDULER_DB_PASSWORD=<scheduler secret>
--
-- Without SCHEDULER_DB_URL the scheduler reuses the primary bank_app pool.
--
-- The application keeps Flyway enabled for validation; spring.flyway.user
-- makes that validation connect as the owner while the Hikari pool connects as
-- bank_app. If the app runs with Flyway disabled and the migration Job is the
-- only writer, FLYWAY_* is optional.
--
-- Re-run this script after every migration batch that adds tables: the default
-- privileges below cover future objects created by the same owner, but the
-- explicit revokes on audit_logs/ledger_entries/audit_chain_state must still
-- be re-applied if one of those tables is ever recreated (e.g. a partition
-- cutover).
--
-- This script is idempotent. It does not store a password.

-- 1. Roles. The app role logs in; the other roles are NOLOGIN capability
--    groups an operator can SET ROLE to (or be granted).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_app') THEN
        EXECUTE 'CREATE ROLE bank_app LOGIN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_audit_writer') THEN
        EXECUTE 'CREATE ROLE bank_audit_writer NOLOGIN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_audit_reader') THEN
        EXECUTE 'CREATE ROLE bank_audit_reader NOLOGIN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_audit_maintenance') THEN
        EXECUTE 'CREATE ROLE bank_audit_maintenance NOLOGIN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_ledger_maintenance') THEN
        EXECUTE 'CREATE ROLE bank_ledger_maintenance NOLOGIN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_scheduler') THEN
        EXECUTE 'CREATE ROLE bank_scheduler LOGIN';
    END IF;
END $$;

-- 2. Session contract: the runtime role mirrors the owner's 30s statement
--    budget, idle-transaction reaping and UTC time zone.
ALTER ROLE bank_app SET statement_timeout = '30s';
ALTER ROLE bank_app SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE bank_app SET timezone = 'UTC';
ALTER ROLE bank_scheduler SET statement_timeout = '30s';
ALTER ROLE bank_scheduler SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE bank_scheduler SET timezone = 'UTC';

-- 3. Connect + schema usage. PUBLIC keeps its default USAGE; CREATE is not
--    granted to the runtime role.
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO bank_app', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO bank_scheduler', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO bank_app;
GRANT USAGE ON SCHEMA public TO bank_audit_writer;
GRANT USAGE ON SCHEMA public TO bank_audit_reader;
GRANT USAGE ON SCHEMA public TO bank_audit_maintenance;
GRANT USAGE ON SCHEMA public TO bank_ledger_maintenance;
GRANT USAGE ON SCHEMA public TO bank_scheduler;
REVOKE CREATE ON SCHEMA public FROM bank_app;

-- 4. Baseline DML for the whole schema, then carve out the evidence tables.
--    The app needs ordinary CRUD on accounts, transfers, users, outbox,
--    idempotency, etc.; audit_logs and ledger_entries are append-only.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO bank_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bank_app;

REVOKE ALL PRIVILEGES ON audit_logs FROM bank_app;
GRANT SELECT, INSERT ON audit_logs TO bank_app;

REVOKE ALL PRIVILEGES ON ledger_entries FROM bank_app;
GRANT SELECT, INSERT ON ledger_entries TO bank_app;

-- The scheduler reads ledger_entries for reconciliation and takes a
-- transaction-scoped advisory lock. It has no write access to any table.
REVOKE ALL PRIVILEGES ON ledger_entries FROM bank_scheduler;
GRANT SELECT ON ledger_entries TO bank_scheduler;

-- The chain head is updated only by the SECURITY DEFINER trigger; the runtime
-- role may read it for the nightly verifier but must never write it directly.
REVOKE ALL PRIVILEGES ON audit_chain_state FROM bank_app;
GRANT SELECT ON audit_chain_state TO bank_app;

-- Flyway history is owner-only. The app validates through spring.flyway.user,
-- not through the runtime pool.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON public.flyway_schema_history FROM bank_app';
    END IF;
END $$;

-- 5. Audit capability roles. bank_audit_writer is granted to the app role so
--    the INSERT grant has a named owner; it still cannot UPDATE/DELETE.
GRANT SELECT, INSERT ON audit_logs TO bank_audit_writer;
GRANT SELECT ON audit_chain_state TO bank_audit_writer;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bank_audit_writer;
GRANT bank_audit_writer TO bank_app;

GRANT SELECT ON audit_logs, audit_chain_state TO bank_audit_reader;
GRANT EXECUTE ON FUNCTION audit_logs_first_broken_chain_seq() TO bank_audit_reader;

GRANT SELECT ON audit_logs, audit_chain_state TO bank_audit_maintenance;
GRANT EXECUTE ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER)
    TO bank_audit_maintenance;

-- 6. Ledger maintenance may DELETE only through the append-only trigger's
--    transaction-local app.ledger_maintenance flag. There is no UPDATE grant.
GRANT SELECT, DELETE ON ledger_entries TO bank_ledger_maintenance;

-- 7. Privileged functions. The retention entry point is not PUBLIC; the app
--    calls it as SECURITY DEFINER and the 30-day cutoff floor applies.
REVOKE ALL ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER) TO bank_app;
GRANT EXECUTE ON FUNCTION delete_audit_logs_before(TIMESTAMP, INTEGER)
    TO bank_audit_maintenance;
GRANT EXECUTE ON FUNCTION audit_logs_assign_chain() TO bank_app;
GRANT EXECUTE ON FUNCTION audit_logs_first_broken_chain_seq() TO bank_app;

-- 8. Enum types are separate ACL objects in PostgreSQL. The app binds them in
--    inserts and filters, so grant USAGE on every public enum to each role.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.typname
          FROM pg_type t
          JOIN pg_namespace n ON n.oid = t.typnamespace
         WHERE n.nspname = 'public'
           AND t.typtype = 'e'
    LOOP
        EXECUTE format('GRANT USAGE ON TYPE public.%I TO bank_app', r.typname);
        EXECUTE format('GRANT USAGE ON TYPE public.%I TO bank_audit_writer', r.typname);
        EXECUTE format('GRANT USAGE ON TYPE public.%I TO bank_audit_reader', r.typname);
        EXECUTE format('GRANT USAGE ON TYPE public.%I TO bank_audit_maintenance', r.typname);
        EXECUTE format('GRANT USAGE ON TYPE public.%I TO bank_ledger_maintenance', r.typname);
    END LOOP;
END $$;

-- 9. Future tables/sequences created by this owner inherit the baseline DML.
--    Re-run the carve-outs above if audit_logs/ledger_entries are recreated.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bank_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO bank_app;

-- 10. Make the post-condition explicit in the psql log.
DO $$
BEGIN
    RAISE NOTICE 'least-privilege roles ready: bank_app (runtime), bank_scheduler (reconciliation), bank_audit_writer, bank_audit_reader, bank_audit_maintenance, bank_ledger_maintenance';
END $$;
