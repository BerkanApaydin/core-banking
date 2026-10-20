-- V57__outbox_retry_backoff.sql
--
-- Owner: infrastructure (outbox)
-- Reason: a failed event was retried on EVERY poll cycle (2s by default), so
-- a poison event burned all 5 retries in ~10 seconds and landed in the dead
-- letter before any transient condition (a slow handler, a brief dependency
-- outage) could clear. max-retries was a retry COUNT, not a retry BUDGET.
--
-- outbox_events.next_attempt_at makes the budget a budget: a failed event
-- is not selected again until its backoff window has passed. The backoff is
-- exponential from app.outbox.retry-backoff-base-seconds, capped by
-- app.outbox.retry-backoff-max-seconds (computed in the adapter, applied
-- with CURRENT_TIMESTAMP so the schedule and the row agree on one clock).
--
-- The pending-scan index (idx_outbox_pending_partition_created, V42) still
-- serves the selection: next_attempt_at is NULL for every event that has
-- never failed, so the extra predicate is a cheap per-row filter on top of
-- the existing (partition, created_at) ordering.
--
-- HIGH-4: lift the 30s per-role statement_timeout for this build.
SET LOCAL statement_timeout = '10min';

ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- Retention deletes processed rows; a processed row must never keep a stale
-- backoff timestamp, and the expression index built in V52 sorts by
-- COALESCE(processed_at, created_at) — unaffected.
COMMENT ON COLUMN outbox_events.next_attempt_at IS
    'Earliest time a failed event may be retried (exponential backoff); NULL = eligible now';
