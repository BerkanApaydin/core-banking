-- V50__audit_transfer_marked_failed.sql
--
-- Owner: audit
-- Reason: the crash-window reaper audits TRANSFER_MARKED_FAILED, which was
-- missing from the native AuditAction enum (audit write rolled back the
-- FAILED transition).
--
-- Adds TRANSFER_MARKED_FAILED to the native "AuditAction" enum (Java side:
-- AuditAction.java): the crash-window reaper (TransferPendingReaper) audits
-- every stale-PENDING-to-FAILED transition, but the value was missing from
-- both the Java enum and this type — the bridge's fromString() threw
-- UnknownAuditActionException and rolled back the FAILED transition, so the
-- reaper retried the same row forever. Safe inside Flyway's transaction on
-- PostgreSQL 12+ (pure DDL, new value unused in this transaction).

ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'TRANSFER_MARKED_FAILED';
