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

`LoginUserUseCaseImpl.auditLogin` and `LogoutUseCaseImpl.auditLogout` catch
every audit failure and only log a warning. MFA enrollment/confirmation/
removal and password-reset request/completion reuse the same best-effort
shape. This is the deliberate opposite of
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

## Historical: standalone audit use cases

The former `AuditLoggerUseCaseImpl`/`@RequiresNewUseCase` path was removed in
the 2026-10 module-boundary refactor. `AuditEventPublisherAdapter` is now the
single audit writer: it joins the caller's transaction when one is active
(mode 1) and is invoked best-effort from authentication flows (mode 2).
`GetAuditLogsQueryImpl` remains read-only; no audit use case opens its own
`REQUIRES_NEW` transaction (see `docs/decisions/transaction-strategy.md`).

## Rule of thumb

Adding a new audit call site? Money movement → mode 1 (pass through the
caller's transaction, no catch). Authentication lifecycle → mode 2 (catch +
warn, never fail the user flow). Post-commit reaction → mode 3 (observe only,
never write).
