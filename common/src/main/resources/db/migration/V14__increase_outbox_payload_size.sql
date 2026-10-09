-- V14__increase_outbox_payload_size.sql
-- Increase outbox payload column to handle large domain event payloads
-- HIGH-4: table rewrite exceeds the 30s per-role statement_timeout on a
-- populated DB. Lift it for this migration only.
SET LOCAL statement_timeout = '10min';

ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT;
