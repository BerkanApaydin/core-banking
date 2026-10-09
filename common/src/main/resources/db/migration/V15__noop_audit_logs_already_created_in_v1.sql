-- NOTE: the filename says "noop" for historical reasons (it was meant as a
-- no-op guard for audit_logs already created in V1), but the body is NOT a
-- noop: it defensively re-creates the table/indexes with IF NOT EXISTS for
-- databases adopted before V1. Do NOT rename (Flyway history); read the body,
-- not the name.
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_username ON audit_logs(username);
