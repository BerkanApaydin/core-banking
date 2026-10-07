-- V39__user_token_version.sql
-- Token generation counter for session invalidation on privilege changes.
-- Issued JWTs carry the version as the `ver` claim; role or password changes
-- bump it, and refresh rotation rejects tokens minted before the bump, so a
-- demoted admin (or a password reset after compromise) loses every session at
-- its next rotation instead of at access-token expiry.
--
-- Plain ADD COLUMN with a default: no legacy rows to backfill (0 is the
-- correct initial generation for every existing user), and raw SQL writers
-- keep working.

ALTER TABLE users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
