# Outbox operations

The outbox provides at-least-once processing. `processed=true` means the local handler returned successfully; `dead_letter=true` means automatic retries stopped. The application does not currently provide a safe operator replay or archive command. Do not reset flags or delete rows as a routine recovery step.

## Partition count changes

Events store the partition number calculated when they are written. A poller configured with `OUTBOX_PARTITION_COUNT=N` scans partitions `0` through `N-1`; a nonpositive count scans all partitions. On startup, the poller now refuses to run when pending, non-dead-letter events exist outside the configured positive range. This is a safety check, not an automatic migration. Check the backlog before changing the count:

```sql
SELECT partition, count(*) AS pending, min(created_at) AS oldest
FROM outbox_events
WHERE processed = false AND dead_letter = false
GROUP BY partition
ORDER BY partition;
```

To change the count, first stop or quiesce event-producing requests, let the existing pollers drain the pending backlog, then stop the old pollers and deploy all instances with the new count. Do not roll out mixed counts while writers are active: the same aggregate can hash to different partitions under each count, weakening event order. For a decrease, restore the previous count if the startup guard reports out-of-range pending events; drain them before retrying the change. No event row needs to be rewritten or deleted for this procedure.

The startup check cannot identify a count change when there is no out-of-range pending row, and it does not prevent an old instance from writing with its old count after a new instance has started. Quiescing writers and switching instances together remains necessary.

## Handlers

`TransferCompletedEvent` and `TransferCancelledEvent` fan out to the
notification channels via `TransferCompletedOutboxRelay` /
`TransferCancelledOutboxRelay`; FAILED transfers publish `TransferFailedEvent`
through `TransferFailedOutboxRelay` (dormant until an async reconciliation
flow marks one), and user registrations through `UserEventOutboxRelay` — all
at-least-once with the key lifecycle below. A durable consumer inbox and explicit replay state
machine are needed before general replay can be automated.

## Key lifecycle

Handler deduplication uses `idempotency_keys` entries with an `outbox_handler_`
prefix (classified by the `key_kind` discriminator since V38, not by string
matching). Each delivery follows a full key lifecycle in
`IdempotentOutboxRelay` (shared skeleton for all relays): claim (`PENDING`)
→ `COMPLETED` on success, `FAILED` on delivery failure (so retention, which
deletes only non-`PENDING` handler keys, can reclaim them instead of leaking
perpetual `PENDING` rows). A `FAILED` claim is reopened exactly once per
retry (`tryResetFailed` is conditional on `FAILED`), so a failed delivery is
retried instead of being mistaken for a delivered duplicate; any other
existing state skips as a genuine duplicate. The generic HTTP idempotency
cleanup preserves these entries, including `PENDING` entries (stuck
reservations stay visible); outbox rows also remain.

## Worker sizing

Workers per partition and the cap moved from literals into
`app.outbox.worker-threads-per-partition` (default 4) and
`app.outbox.max-worker-threads` (default 12); the bounded worker wait is
`app.outbox.worker-timeout-seconds` (default 30, aligned with the use-case
transaction timeout). Env overrides: `OUTBOX_WORKERS_PER_PARTITION`,
`OUTBOX_MAX_WORKERS`, `OUTBOX_WORKER_TIMEOUT_SECONDS` (see `.env.example`).
Keep `workers-per-partition × partition-count` under the Hikari pool.

## Dead letters and replay

Inspect dead letters and their error before deciding whether the handler can safely run again:

```sql
SELECT id, event_type, aggregate_type, aggregate_id, retry_count,
       created_at, last_error
FROM outbox_events
WHERE dead_letter = true
ORDER BY created_at, id;
```

Handler deduplication uses `idempotency_keys` entries with an `outbox_handler_` prefix (see Key lifecycle above). A manual replay of an already processed event will therefore normally skip its handler. However, dedup entries deleted before this protection was deployed cannot be reconstructed from an outbox row, and a provider-facing side effect may have succeeded before a crash even when the outbox transaction rolled back. Confirm downstream state and dedup history before any manual replay. A durable consumer inbox and explicit replay state machine are needed before general replay can be automated.

## Retention

Processed, non-dead-letter outbox rows older than `app.outbox.retention-days`
(default 30) are deleted by `OutboxRetentionScheduler` (weekly by default,
`app.outbox.retention-cron`), followed by `outbox_handler_*` dedup keys older
than the same cutoff — in that order, so a dedup key never disappears while
its event row may still exist. Unprocessed rows (redelivery source),
dead-letter rows (investigation evidence) and `PENDING` keys (stuck
reservations stay visible) are never auto-deleted. `refresh_tokens` expiry
hygiene runs separately (`RefreshTokenCleanupJob`, daily); `audit_logs` and
`ledger_entries` have no retention — they are the legal/audit trail, not a
queue. In particular, do not use the HTTP idempotency cleanup interval
(24h) as an outbox retention period.
