-- Store only a digest of the bearer token. PostgreSQL is the durable source
-- for revocations written by the database/hybrid backends.
CREATE TABLE token_revocations (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_token_revocations_expires_at ON token_revocations (expires_at);
