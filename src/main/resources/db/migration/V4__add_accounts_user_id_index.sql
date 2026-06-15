-- V4__add_accounts_user_id_index.sql
-- Index for accounts.user_id column to optimize listing accounts by user.
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
