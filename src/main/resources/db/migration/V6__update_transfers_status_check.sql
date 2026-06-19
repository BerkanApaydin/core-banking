-- V6__update_transfers_status_check.sql
-- Drop the old status check constraint and add a new one including PENDING and FAILED.
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS chk_transfers_status;
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'));
