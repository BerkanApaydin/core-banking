-- Owner: user
-- Reason: MFA (TOTP) enrollment + password-reset token store (audit P1: MFA
-- and password-reset flows were missing entirely)
--
-- V54__mfa.sql
--
-- users.mfa_enabled gates the second factor at login; the TOTP secret itself
-- lives in users.mfa_secret_encrypted as AES-GCM ciphertext (base64), never
-- plaintext: a database read must never yield an enrollable secret (same rule
-- as TokenDigest for bearer/refresh tokens). The encryption key comes from
-- the MFA_ENCRYPTION_KEY environment (see MfaProperties + fail-fast prod
-- validation in ApplicationStartupValidator); the DB layer only stores bytes.
--
-- password_reset_tokens holds single-use reset token digests (SHA-256 hex, 64
-- chars — same convention as refresh token digests). Raw tokens travel only
-- through the out-of-band notification channel and are never persisted.
-- No heavyweight statements: plain ADD COLUMN with defaults + one small table.

ALTER TABLE users
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret_encrypted VARCHAR(512);

CREATE TABLE password_reset_tokens (
    -- VARCHAR (not CHAR): matches the JPA mapping convention of
    -- refresh_tokens.token_hash (Hibernate validates CHAR as bpchar).
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    -- AuditableJpaEntity columns (same shape as refresh_tokens, V31).
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
