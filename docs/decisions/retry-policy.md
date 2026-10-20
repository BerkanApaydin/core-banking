# T-6: Unified Retry Policy

**Decision:** The two retry paths (`@Idempotent` guard + transfer use-case
aspect) share one policy class: `ExponentialBackoffPolicy(initial, max, jitter)`
plus a `Sleeper` abstraction.

- `IdempotentRetryExecutor`: policy + injectable `Sleeper` (prod: `Thread.sleep`,
  test: no-op recorder). Only lock failures retry — 4xx never sleeps.
- `TransferUseCaseRetryAspect`: same policy (100ms jitter); no retry inside an
  existing transaction (rollback-only reuse guard).

**Why two mechanisms remain:** The layers differ (HTTP idempotency boundary vs
use-case lock retry). Unified *policy*, separate *application points* — one
behavior, two call sites. Full unification (single aspect) would bury the
idempotency semantics (PENDING/COMPLETED replay) inside transfer retry;
rejected.

**Test:** `ExponentialBackoffPolicyTest` asserts the sequence deterministically;
the `Sleeper` recorder captures delays without sleeping.

## Outbox retry backoff (2026-10, V57)

**Decision:** a failed outbox event is not selected again until its backoff
window has passed: `base * 2^(retry-1)` seconds, capped
(`app.outbox.retry-backoff-base-seconds` = 5, `...-max-seconds` = 300).
The delay is computed in the adapter and applied by the statement with the
DATABASE clock (`outbox_events.next_attempt_at`).

**Why:** `max-retries` was a retry COUNT, not a retry BUDGET. A poison event
was retried on every poll cycle (2s by default), so all five attempts were
burned within ~10 seconds and the event dead-lettered long before a
transient condition (a slow handler, a brief dependency outage) could
clear. With the backoff the same five attempts span ~2.5 minutes.

**Not a member of the unified policy class:** the outbox backoff is applied
in SQL (a column the selection query filters on), not as a sleep before a
retry. A sleeping worker would hold a Hikari connection for the whole
delay — the opposite of what the outbox budget is for.
