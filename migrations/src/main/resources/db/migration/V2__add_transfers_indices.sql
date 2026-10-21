-- V2__add_transfers_indices.sql
-- Add indices to optimize queries on transfers table
CREATE INDEX idx_transfers_sender_account_id ON transfers (sender_account_id);
CREATE INDEX idx_transfers_receiver_account_id ON transfers (receiver_account_id);
CREATE INDEX idx_transfers_created_at ON transfers (created_at);
