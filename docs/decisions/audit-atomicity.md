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

`LoginUserUseCaseImpl.auditLogin` and the other authentication call sites catch
every audit failure and only log a warning. An audit-store outage must not lock
users out of login or turn logout into a 500 after revocation already happened.
The gap is observable (warn logs); it is not silent.

Logout additionally uses the durable variant:
`LogoutUseCaseImpl.auditLogout` calls `AuditEventPort.publishBestEffort`.
`AuditEventPublisherAdapter` first tries the normal synchronous write; when that
fails it enqueues the `AuditEvent` through `AuditRetryOutboxAdapter`
(`REQUIRES_NEW`) and logs. `AuditRetryOutboxRelay` replays the outbox row with
the standard retry/dead-letter policy and writes the audit row through
`SaveAuditLogPort`. A dispatch failure after a successful row write is only
logged: the row is already durable and must not be duplicated by a retry.
The fallback is observable through `audit.retry.enqueued` / `audit.retry.failed`
(tagged by action); `AuditRetryEnqueued` is a ticket and
`AuditRetryEnqueueFailed` is a page because the latter is the only path that
still loses the audit row.

The remaining authentication call sites still use the plain `publish` inside
their own catch block and therefore have no durable retry yet. Migrating them to
`publishBestEffort` is a follow-up, one call site at a time; the port method is
already available so the retry outbox type is shared.

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
single synchronous audit writer: it joins the caller's transaction when one is
active (mode 1) and is invoked best-effort from authentication flows (mode 2).
The retry outbox is the only deferred writer (`AuditRetryOutboxRelay`), and its
enqueue deliberately opens `REQUIRES_NEW` so the retry row survives a failing
caller transaction. `GetAuditLogsQueryImpl` remains read-only; no audit use case
opens its own `REQUIRES_NEW` transaction (see
`docs/decisions/transaction-strategy.md`).

## Rule of thumb

Adding a new audit call site? Money movement → mode 1 (pass through the
caller's transaction, no catch). Authentication lifecycle → mode 2 (catch +
warn, never fail the user flow; prefer `publishBestEffort` when the event must
survive an audit-store outage). Post-commit reaction → mode 3 (observe only,
never write).
