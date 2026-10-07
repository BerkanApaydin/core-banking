# Transfer FAILED State Retention (S10/D18)

## Status

Accepted — `Transfer.markFailed()`, `TransferStatus.FAILED`, the `chk_transfers_status`
values and the report UI's FAILED rendering stay, although no production flow
calls `markFailed()` today.

## Context

The synchronous placement path (`PlaceTransferUseCaseImpl`) rolls the
transaction back on failure instead of persisting a FAILED row, so a failure
never leaves a half-written transfer behind. `markFailed()` is therefore dead
production code by the strict YAGNI reading.

## Decision

Keep it, for three coupled reasons — removing any one leg would force
removing all three, which is the actual cost:

1. **State machine completeness.** `TransferStatus` without FAILED cannot
   represent an asynchronous placement outcome. The status CHECK constraint,
   the native enum (V32) and the report UI already render FAILED; deleting the
   transition would leave a reachable state with no transition into it.
2. **Async placement is the documented next step** for moving Account out of
   the monolith (README Saga note): the PENDING row must survive a worker
   crash and be reconcilable afterwards — exactly what `markFailed()` does.
3. **Cost of retention is ~10 lines** with unit tests (`TransferTest` covers
   the PENDING→FAILED transition and its event).

## Consequences

- `Transfer.cancel()` keeps rejecting FAILED transfers explicitly
  (`TransferNotCancellableException`), so the retained state cannot be
  misused as cancellable.
- If async placement is still absent after the next two release cycles,
  revisit: delete the method, keep the enum value (DB CHECK/enum compat).

## Alternatives considered

- Delete now: saves 10 lines, but re-adding later means touching the domain,
  the DB constraint, the enum type and the UI at once — the same coupled
  change in reverse.
- `@VisibleForTesting`: rejected — the method is production API for a future
  flow, not a test seam.
