-- V55__mfa_audit_actions.sql
--
-- Owner: audit
-- Reason: MFA enrollment/removal and password-reset request/completion are
-- security-sensitive and audited (Java side: AuditAction.java + AuditEvent).
--
-- Adds MFA_ENABLED, MFA_DISABLED, PASSWORD_RESET_REQUESTED and
-- PASSWORD_RESET_COMPLETED to the native "AuditAction" enum. Safe inside
-- Flyway's transaction on PostgreSQL 12+ (pure DDL, new values unused in
-- this transaction). ALTER TYPE ... ADD VALUE takes a type lock only
-- (see check_migration_timeouts.py NOTE) and needs no timeout lift.

ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'MFA_ENABLED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'MFA_DISABLED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'PASSWORD_RESET_REQUESTED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'PASSWORD_RESET_COMPLETED';
