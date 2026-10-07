-- V31__create_refresh_tokens.sql
-- Server-side refresh-token sessions for rotation + reuse detection.
-- Only digests are stored (see TokenDigest): a database read never yields a
-- usable credential. One row per issued refresh token; rotation marks the old
-- row revoked (linking to its replacement), reuse of a rotated token revokes
-- the whole family. Expired rows are deleted by the cleanup job; they are
-- never updated. No user FK by design (like V26 token_revocations): there is
-- no user-deletion flow, and the store must stay append-only evidence.
-- New table: no legacy rows, no NOT VALID dance needed.

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_hash VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(255),
    updated_at TIMESTAMP(6),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id
    ON refresh_tokens (family_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
