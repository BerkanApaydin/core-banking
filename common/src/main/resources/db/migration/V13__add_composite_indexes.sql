-- V13__add_composite_indexes.sql
-- Add composite indexes for report queries and common access patterns

CREATE INDEX IF NOT EXISTS idx_transfers_sender_date
    ON transfers (sender_account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_receiver_date
    ON transfers (receiver_account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id_status
    ON accounts (user_id, status);

CREATE INDEX IF NOT EXISTS idx_outbox_processed_created
    ON outbox_events (processed, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_logs_username_action
    ON audit_logs (username, action);
