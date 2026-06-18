-- V8__fix_column_types.sql
-- Increase column limits to avoid data truncation
ALTER TABLE audit_logs ALTER COLUMN details TYPE TEXT;
ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT;
