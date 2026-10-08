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
