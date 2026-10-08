# R-1: Stale-PENDING Reaper (Crash-Window Recovery)

## Status

Accepted — `TransferPendingReaper` (transfer `adapter.in.scheduler`) marks
stale PENDING rows FAILED through the domain on a 5-minute schedule.

## Context

The synchronous placement path (`PlaceTransferUseCaseImpl`) persists the
PENDING row first (the ID is needed for the COMPLETED event) and rolls
everything back on failure — except a JVM crash between the two saves, which
leaves a PENDING row whose money movement never happened. Before this
decision, nothing ever revisited such rows: reports showed phantom pending
transfers and `Transfer.markFailed()` had no production caller (see
`transfer-failed-state.md`: retained for async placement, dead in practice).

## Decision

1. **Reaper lives in the transfer module** (`adapter.in.scheduler`), not in
   infrastructure: `ModuleBoundariesArchitectureTest` forbids
   `infrastructure → transfer`, so the job uses the context's own
   `LoadTransferPort`/`SaveTransferPort`/`AuditEventPort`.
2. **No distributed lock.** Each candidate row is re-locked
   (`findByIdForUpdate`) and written through the versioned bulk UPDATE. A
   racing completion/cancellation — or another replica's reaper — surfaces as
   `TransferNotPendingException` / optimistic-lock failure, counted as
   conflict, never retried blindly. Stale threshold (default 15 min) stays
   far above the 30 s use-case transaction timeout so slow placements are
   never reaped.
3. **No domain-event publish.** There is no outbox relay for FAILED yet; an
   unhandled event type would poison the outbox. The `TRANSFER_MARKED_FAILED`
   audit row is the trail until a FAILED consumer lands (then wire
   `DomainEventPublisherService` + relay first).
4. **Per-row isolation:** one poisoned row never starves the batch; a failed
   scan only delays detection (next schedule retries). Counters
   `transfer.pending.reaped` / `transfer.pending.reap-conflicts` feed the
   `transfers` alert group (sustained reaping pages as crash evidence).

## Consequences

- `Transfer.markFailed()` finally has a production caller; the FAILED state
  is reachable and reconcilable, closing the YAGNI concern in
  `transfer-failed-state.md`.
- Operators tune `app.transfer.reaper.{older-than,batch-size,cron}`; the
  reap-conflicts alert tells them when the threshold races live traffic.
- If async placement lands, the reaper stays: worker crashes produce the
  same leftovers, only more often.

## Alternatives considered

- Advisory-lock single-flight (K12/D5 pattern): rejected — the lock class
  lives in infrastructure (unreachable without breaking module boundaries)
  and row-level versioning already gives exactly-once effect per row.
- Raw-SQL status flip: rejected — bypasses the domain transition guard and
  the `@Version` check; a racing completion could be silently overwritten.
- FAILED over outbox publish: rejected until a relay exists (poison risk).
