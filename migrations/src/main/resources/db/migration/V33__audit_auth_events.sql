-- V33__audit_auth_events.sql
-- Authentication lifecycle audit coverage (K11/D4). Until now AuditAction
-- covered only money movements; the highest-value audit trail — who tried to
-- authenticate, who succeeded, whose sessions died — lived only in Redis
-- login-attempt counters that expire after the brute-force window.
--
-- Adds five values to the native "AuditAction" enum (Java side:
-- AuditAction.java). PASSWORD_CHANGED is reserved for a future
-- password-change flow (no such endpoint exists yet); ACCOUNT_IBAN_VIEWED
-- stays out deliberately (read-path volume belongs in access logs, not the
-- append-only audit ledger).
--
-- Safe online: ADD VALUE takes a brief lock on the enum type only, and no
-- existing rows are rewritten. Safe inside Flyway's migration transaction on
-- PostgreSQL 12+: added values simply must not be *used* in the same
-- transaction that adds them — this file performs pure DDL.

ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'LOGIN_SUCCEEDED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'LOGIN_FAILED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'LOGOUT';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'PASSWORD_CHANGED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'TOKEN_REVOKED';
