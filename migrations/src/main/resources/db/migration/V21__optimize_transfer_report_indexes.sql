-- V21__optimize_transfer_report_indexes.sql
-- Report/history queries filter on business time and order by insert time.
-- V20 added business_created_at without an index; range reports (BETWEEN) would
-- fall back to sequential scans on large transfer tables. The V16 composite
-- (sender, receiver, created) cannot serve OR-history queries
-- (sender=? OR receiver=?) so it is dropped in favour of the two dedicated
-- per-side indexes from V13 plus the new business-time index.

CREATE INDEX IF NOT EXISTS idx_transfers_business_created_at
    ON transfers (business_created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_created_at_desc
    ON transfers (created_at DESC);

DROP INDEX IF EXISTS idx_transfers_sender_receiver_date;
