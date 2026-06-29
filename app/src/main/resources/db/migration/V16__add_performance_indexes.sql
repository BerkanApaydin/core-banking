-- V16__add_performance_indexes.sql
-- Add indexes to improve query performance under load

-- Index for idempotency cleanup scheduler (queries by created_at)
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at
    ON idempotency_keys (created_at);

-- Composite index for transfer history queries (OR pattern on sender/receiver)
-- Covers: WHERE sender_account_id = ? OR receiver_account_id = ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_transfers_sender_receiver_date
    ON transfers (sender_account_id, receiver_account_id, created_at DESC);

-- Index for account lookup by IBAN (used in authorization check for every transfer)
-- IBAN already has a UNIQUE constraint which creates an index, but an explicit
-- index on (iban, user_id) covers the AuthorizationService's lookup pattern
CREATE INDEX IF NOT EXISTS idx_accounts_iban_user
    ON accounts (iban, user_id);

-- Index for transfer detail queries by ID with status filter
CREATE INDEX IF NOT EXISTS idx_transfers_status_created
    ON transfers (status, created_at DESC);
