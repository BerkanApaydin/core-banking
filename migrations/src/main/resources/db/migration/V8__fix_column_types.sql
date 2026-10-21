-- V8__fix_column_types.sql
-- Increase column limits to avoid data truncation
-- HIGH-4: column rewrites exceed the 30s per-role statement_timeout on a
-- populated DB. Lift it for this migration only.
SET LOCAL statement_timeout = '10min';

ALTER TABLE audit_logs ALTER COLUMN details TYPE TEXT;
ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT;

-- Fix status CHECK constraint to match TransferStatus enum
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS chk_transfers_status;
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- Remove NOT NULL from version column (managed by Hibernate @Version)
ALTER TABLE transfers ALTER COLUMN version DROP NOT NULL;
