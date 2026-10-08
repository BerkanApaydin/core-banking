-- V47__refresh_token_user_index.sql
--
-- Per-user session lookup for refresh_tokens. V31 indexed only family_id
-- (rotation chain) and expires_at (cleanup); a user-scoped query — "revoke
-- every session of user X" (account compromise, support lockout) — would
-- otherwise full-scan. No query needs it yet, but the admin token-version
-- re-validation (SEC-01, LoadUserPort.findById) and any future bulk-revoke
-- both resolve users first and sessions second; the index makes that join
-- order cheap from day one.
--
-- Consistency with V31/V28 (plain CREATE INDEX IF NOT EXISTS): Flyway runs
-- each migration in one transaction and CONCURRENTLY is illegal there. The
-- table is small (one row per live refresh session, 7-day TTL with cleanup),
-- so the brief SHARE lock is negligible. Revisit CONCURRENTLY only if the
-- table ever grows past the cleanup horizon.

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);
