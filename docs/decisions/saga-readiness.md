# Saga Readiness (K18)

## Status

Documented path, not yet implemented — the monolith does not need sagas today.

## Why not now

`PlaceTransferUseCaseImpl` / `CancelTransferUseCaseImpl` mutate transfer +
accounts + ledger + audit in ONE local PostgreSQL transaction
(`UseCaseTransactionAspect`, REQUIRED). Atomicity is free; a saga would add
failure modes for zero benefit while everything shares one DataSource.

## Trigger

The day the Account context leaves the monolith (separate service/DB), the
`accountAclPort.debitAndCredit` / `reverseBalancesForCancellation` calls stop
being local-transaction participants. Both call sites are marked with the same
comment (`"If the Account context ever moves to a separate service, this
needs a Saga"`) — that comment is the tripwire.

## Pre-built foundations (already in the codebase)

- **Transactional outbox** (`outbox_events`, partitioned polling, SKIP LOCKED,
  dead letter): the saga event bus. Transfer lifecycle events
  (`TransferCompletedEvent`, `TransferCancelledEvent`) already flow through it
  via `TransferCompletedOutboxRelay` / `TransferCancelledOutboxRelay`.
- **Idempotency guard** (`Idempotency-Key` + request hash + FAILED→PENDING
  claim): saga steps are safely retryable and de-duplicated.
- **Compensating action**: `reverseForCancellation` already reverses a
  completed transfer with the same ordered-locking and double-entry journal —
  it becomes the saga's compensation step nearly unchanged.
- **Double-entry ledger** (`ledger_entries`, shared `transaction_ref`): the
  saga's net-zero reconciliation query keeps working across services as long
  as both legs share one `transaction_ref`.

## Remaining work when triggered

1. Choreography vs orchestration decision (recommendation: orchestrate from
   `transfer` — it already owns the lifecycle state machine PENDING →
   COMPLETED → CANCELLED/FAILED).
2. Outbox relay from transfer → account command topic; account → transfer
   receipt topic; timeout + compensation on missing receipt.
3. `TransferStatus` gains no new values (PENDING already means "awaiting
   remote confirmation" in that world); `markFailed()` — retained per
   `transfer-failed-state.md` — becomes the live path for unconfirmed PENDING
   rows instead of a future path.
