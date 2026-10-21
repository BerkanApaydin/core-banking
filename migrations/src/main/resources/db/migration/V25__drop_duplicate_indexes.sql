-- V13 recreated three existing V3/V5 indexes under different names. Keep the
-- original indexes, which have the same columns and ordering, to avoid extra
-- write amplification and storage on transfers and outbox_events.
DROP INDEX IF EXISTS idx_transfers_sender_date;
DROP INDEX IF EXISTS idx_transfers_receiver_date;
DROP INDEX IF EXISTS idx_outbox_processed_created;
