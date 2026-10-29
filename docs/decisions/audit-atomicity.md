# Audit write atomicity: three named modes

Audit records are written through three paths with deliberately different
atomicity. The names below are the vocabulary for incident review and code
comments — do not add a fourth mode without updating this decision.

## 1. `mandatory-same-tx` — money movements (default for financial writes)

`AuditEventPublisherAdapter.publish` persists the `AuditLog` row with no
`@Transactional` of its own, so it joins the caller's use-case transaction
(`@TransactionalUseCase` in `PlaceTransferUseCaseImpl`,
`CancelTransferUseCaseImpl`, `CreateAccountUseCaseImpl`,
`AdjustAccountBalancesUseCaseImpl`). A failed audit write rolls back the money
movement with it: money without its audit trail must never commit.
`readme.md` ("Mandatory audit records … rolls the movement back") states the
user-visible contract.

## 2. `best-effort-auth` — login / logout / refresh / MFA / reset lifecycle

All authentication audit call sites use `AuditEventPort.publishBestEffort`
(`LoginUserUseCaseImpl`, the stale-generation rejection in
`RefreshSessionUseCaseImpl`, `RequestPasswordResetUseCaseImpl`,
`ConfirmPasswordResetUseCaseImpl`, `EnableMfaUseCaseImpl` for enable/disable,
`MfaReauthenticationService` for re-auth failures, and `LogoutUseCaseImpl`).
The refresh-family theft path deliberately stays on mandatory `publish`: the
revoke and its audit row must commit or roll back together (K11/D4).

`publishBestEffort` never lets an audit failure change the auth outcome. With an
active caller transaction it defers the write until that transaction completes
and submits it to the single-thread `auditBestEffortExecutor`; the executor
opens the audit transaction with `REQUIRES_NEW`. Deferring matters because a
synchronous `REQUIRES_NEW` write while the caller still holds its connection
doubles connection demand and can deadlock a small pool under concurrent auth
requests. Without a caller transaction the write runs synchronously in
`REQUIRES_NEW`.

If the audit write fails, the adapter enqueues the `AuditEvent` through
`AuditRetryOutboxAdapter` (`REQUIRES_NEW`) and logs. `AuditRetryOutboxRelay`
replays the outbox row with the standard retry/dead-letter policy and writes the
audit row through `SaveAuditLogPort`. A dispatch failure after a successful row
write is only logged: the row is already durable and must not be duplicated by a
retry. The fallback is observable through `audit.retry.enqueued` /
`audit.retry.failed` (tagged by action); `AuditRetryEnqueued` is a ticket and
`AuditRetryEnqueueFailed` is a page. Executor queue rejection is the remaining
loss path: it increments `audit.best_effort.rejected` (`AuditBestEffortRejected`,
page) and logs an error, but it does not fall back to a synchronous write (that
would reintroduce the pool amplification).

## 3. `observe-after-commit` — dispatch seam, writes nothing

`AuditEventConsumer.onAuditEvent` runs as a `@TransactionalEventListener`
(`AFTER_COMMIT`) and performs zero database work (in-memory metrics only, see
`MicrometerAuditFailureAdapter`). It must never gain a transaction:
`REQUIRES_NEW` here would pool-starve against the money transaction and can
deadlock the commit (documented on the listener). The row is already durable
via mode 1 before this listener ever runs.

## Historical: standalone audit use cases

The former `AuditLoggerUseCaseImpl`/`@RequiresNewUseCase` path was removed in
the 2026-10 module-boundary refactor. `AuditEventPublisherAdapter` is now the
single audit-row writer: it joins the caller's transaction when one is active
(mode 1); mode 2 either runs on the after-completion executor or, without a
caller transaction, synchronously. `AuditRetryOutboxRelay` is the only replay
path, and its enqueue deliberately opens `REQUIRES_NEW` so the retry row
survives a failing caller transaction. `GetAuditLogsQueryImpl` remains
read-only; no audit use case opens its own `REQUIRES_NEW` transaction — the
adapter does, either on the after-completion executor for mode 2 or inside the
retry enqueue (see `docs/decisions/transaction-strategy.md`).

## Rule of thumb

Adding a new audit call site? Money movement → mode 1 (pass through the
caller's transaction, no catch). Authentication lifecycle → mode 2
(`publishBestEffort`; catch + warn, never fail the user flow). The refresh-family
theft revoke stays mandatory same-tx because it mutates security state.
Post-commit reaction → mode 3 (observe only, never write).
