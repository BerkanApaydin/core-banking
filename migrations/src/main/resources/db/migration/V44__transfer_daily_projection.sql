-- V44__transfer_daily_projection.sql
--
-- Mid-term #2: report read model. Whole-range COUNT+SUM scans the table on
-- every request; this materialized view keeps pre-aggregates at daily
-- granularity. The application reads this view first and completes missing
-- days via summarizeRange (gradual rollout: the old path works when the view
-- is absent).
-- REFRESH: nightly cron in the same leader-lock pattern as
-- OutboxRetentionScheduler (application-side
-- TransferReportProjectionRefresher, CONCURRENTLY).

CREATE MATERIALIZED VIEW IF NOT EXISTS transfer_daily_totals AS
SELECT
    LEAST(sender_account_id, receiver_account_id) AS account_a,
    GREATEST(sender_account_id, receiver_account_id) AS account_b,
    sender_account_id AS sender_account_id,
    receiver_account_id AS receiver_account_id,
    date_trunc('day', business_created_at)::date AS day,
    COUNT(*) AS transfer_count,
    SUM(amount) AS total_volume
FROM transfers
GROUP BY sender_account_id, receiver_account_id, date_trunc('day', business_created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transfer_daily_totals_uniq
    ON transfer_daily_totals (sender_account_id, receiver_account_id, day);
