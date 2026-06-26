-- V14__increase_outbox_payload_size.sql
-- Increase outbox payload column to handle large domain event payloads

ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT;
