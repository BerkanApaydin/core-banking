-- V18__add_users_email_index.sql
-- Index for users.email column to optimize lookups by email
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
