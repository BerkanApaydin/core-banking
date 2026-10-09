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

## 2. `best-effort-auth` — login / logout / refresh lifecycle

`LoginUserUseCaseImpl.auditLogin` and `LogoutUseCaseImpl.auditLogout` catch
every audit failure and only log a warning. This is the deliberate opposite of
mode 1: an audit-store outage must not lock users out of login or turn logout
into a 500 after revocation already happened. The gap is observable (warn
logs); it is not silent.

## 3. `observe-after-commit` — dispatch seam, writes nothing

`AuditEventConsumer.onAuditEvent` runs as a `@TransactionalEventListener`
(`AFTER_COMMIT`) and performs zero database work (in-memory metrics only, see
`MicrometerAuditFailureAdapter`). It must never gain a transaction:
`REQUIRES_NEW` here would pool-starve against the money transaction and can
deadlock the commit (documented on the listener). The row is already durable
via mode 1 before this listener ever runs.

## Standalone audit use cases

`AuditLoggerUseCaseImpl` declares `@RequiresNewUseCase` in its own module;
`UseCaseTransactionAspect` interprets the marker as `REQUIRES_NEW` (the legacy
`audit.application.usecase` package pointcut remains as a fail-safe OR).
`GetAuditLogsQueryImpl` is read-only. Audit writes therefore never join a
business transaction when invoked outside the money path (see
`docs/decisions/transaction-strategy.md`).

## Rule of thumb

Adding a new audit call site? Money movement → mode 1 (pass through the
caller's transaction, no catch). Authentication lifecycle → mode 2 (catch +
warn, never fail the user flow). Post-commit reaction → mode 3 (observe only,
never write).
