-- V17__add_accounts_user_created_index.sql
-- Add composite index for GET /accounts query:
--   SELECT * FROM accounts WHERE user_id = ? ORDER BY created_at DESC
--
-- Before: idx_accounts_user_id (user_id) covers WHERE, but ORDER BY requires a separate sort step.
-- After:  (user_id, created_at DESC) covers both WHERE and ORDER BY — index-only scan, no sort.

CREATE INDEX IF NOT EXISTS idx_accounts_user_id_created_at
    ON accounts (user_id, created_at DESC);
