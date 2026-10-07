-- V32__native_enum_types.sql
-- Promotes the code columns from VARCHAR to native PostgreSQL ENUMs. The V3 /
-- V6 / V8 / V28 CHECK constraints proved the value sets; the database now
-- enforces them as types, so the redundant CHECKs are dropped (single source
-- of truth). Java side maps them with @Enumerated + NAMED_ENUM, whose type
-- names are the enum simple names (quoted: mixed case).
-- Safe online on small tables (brief ACCESS EXCLUSIVE per ALTER); assess lock
-- time in staging before rolling against a large transfers table.

CREATE TYPE "AccountStatus" AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED');
CREATE TYPE "Currency" AS ENUM ('TRY', 'USD', 'EUR');
CREATE TYPE "TransferStatus" AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE "Role" AS ENUM ('ROLE_USER', 'ROLE_ADMIN');
CREATE TYPE "LedgerDirection" AS ENUM ('DEBIT', 'CREDIT');
CREATE TYPE "AuditAction" AS ENUM (
    'ACCOUNT_CREATED', 'ACCOUNT_DEBITED', 'ACCOUNT_CREDITED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_CLOSED',
    'TRANSFER_EXECUTED', 'TRANSFER_CANCELLED');

ALTER TABLE accounts ALTER COLUMN status TYPE "AccountStatus" USING status::"AccountStatus";
ALTER TABLE accounts ALTER COLUMN currency TYPE "Currency" USING currency::"Currency";
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_accounts_status;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_accounts_currency;

ALTER TABLE transfers ALTER COLUMN status TYPE "TransferStatus" USING status::"TransferStatus";
ALTER TABLE transfers ALTER COLUMN currency TYPE "Currency" USING currency::"Currency";
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS chk_transfers_status;
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS chk_transfers_currency;

ALTER TABLE users ALTER COLUMN role TYPE "Role" USING role::"Role";
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;

ALTER TABLE ledger_entries ALTER COLUMN direction TYPE "LedgerDirection" USING direction::"LedgerDirection";
ALTER TABLE ledger_entries ALTER COLUMN currency TYPE "Currency" USING currency::"Currency";

ALTER TABLE audit_logs ALTER COLUMN action TYPE "AuditAction" USING action::"AuditAction";
