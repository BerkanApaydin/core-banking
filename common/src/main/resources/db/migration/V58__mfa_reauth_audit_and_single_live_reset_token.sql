-- V58__mfa_reauth_audit_and_single_live_reset_token.sql
--
-- Owner: user + audit
-- Reason (R-U08): a wrong password on a session-authenticated MFA management
-- endpoint (enable/confirm/disable) is a security event. The Java enum
-- AuditAction gains MFA_REAUTH_FAILED; the value is added to the native type
-- here. The enum change is DDL and the new value is unused in this
-- transaction (safe on PostgreSQL 12+, same pattern as V55).
--
-- Reason (R-U12): RequestPasswordResetUseCaseImpl did
--   DELETE unused tokens FOR user; INSERT new token
-- without serialization. Two concurrent requests could both delete nothing
-- and then both insert, leaving two live reset tokens. The partial unique
-- index below is the database-level invariant: at most one unused token per
-- user. The application serializes the delete+insert with a pessimistic lock
-- on the user row (LoadUserPort.findByUsernameForUpdate), so the normal path
-- never hits the constraint; the index backstops it.

ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'MFA_REAUTH_FAILED';

-- Keep the newest unused token per user (created_at, then token_hash as a
-- deterministic tie-breaker) before the unique index is built. Used/expired
-- rows are untouched: the index only covers used_at IS NULL.
DELETE FROM password_reset_tokens old
USING password_reset_tokens newer
WHERE old.used_at IS NULL
  AND newer.used_at IS NULL
  AND old.user_id = newer.user_id
  AND (newer.created_at > old.created_at
       OR (newer.created_at = old.created_at AND newer.token_hash > old.token_hash));

CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_tokens_one_live_per_user
    ON password_reset_tokens (user_id)
    WHERE used_at IS NULL;
