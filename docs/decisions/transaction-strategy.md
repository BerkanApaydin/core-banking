# Transaction Strategy (5.1)

Two mechanisms coexist deliberately — this note records which to use where.

## Use cases: AOP programmatic transactions

`@TransactionalUseCase` / `@ReadOnlyUseCase` marker annotations are turned into
transactions by `UseCaseTransactionAspect` (programmatic
`PlatformTransactionManager` usage). The aspect centralizes two policies no
single `@Transactional` can express uniformly:

- explicit 30s timeout (`app.transaction.timeout-seconds`) so hung
  `PESSIMISTIC_WRITE` row locks cannot exhaust the Hikari pool;
- explicit `READ_COMMITTED` isolation (the locking strategy is pessimistic, so
  `REPEATABLE_READ`/`SERIALIZABLE` would only add 40001/40003 retries).

The former `@RequiresNewUseCase` marker was removed with the standalone audit
use case; mandatory audit writes join the caller transaction, while best-effort
authentication audit writes run after completion on a dedicated executor in
their own transaction (see `audit-atomicity.md`).

**Rule: application-layer code (`..application..`) must never use
`@Transactional`** — enforced by
`LayeringArchitectureTest.applicationLayerShouldNotDependOnSpringTransaction`.

## Adapters: declarative `@Transactional`

Single-boundary adapter methods (`OutboxProcessor`, `IdempotencyGuard`,
`*PersistenceAdapter`) use declarative `@Transactional` because each owns
exactly one transaction boundary with its own propagation (typically
`REQUIRES_NEW` for claim/checkpoint writes that must survive the caller's
rollback). Every such annotation carries
`timeoutString = "${app.transaction.timeout-seconds:30}"` to follow the
single-sourced use-case budget (never a hardcoded `timeout = 30`, which
would drift when the property changes); read-only lookups stay bare
`@Transactional(readOnly = true)`.

**Rule: never nest a declarative transaction inside `AdvisorySchedulerLock` —
the guard owns its transaction, and a contended lock marks rollback-only.**

## Why not one mechanism?

Moving use cases to `@Transactional` would scatter timeout/isolation policy
across the use-case classes with no compile-time exhaustiveness. Moving adapters to
the aspect would force business-shaped markers (`@TransactionalUseCase`) onto
infrastructure concerns (outbox polling, idempotency claims) that are not use
cases. The split follows the layer, not the author.

## Adapters that only observe the boundary: TransactionBoundaryPort

Two adapters needed transaction *visibility* without owning a boundary: AccountApiAdapter (defer cache eviction to AFTER_COMMIT) and TransferUseCaseRetryAspect (suppress retry inside an existing tx). They previously imported Spring transaction support classes directly, pinning bounded contexts to Spring.

Since 2026-10 both go through TransactionBoundaryPort (common, framework-free: isTransactionActive,
runAfterCommit, runAfterCompletion, executeRequiresNew). The single Spring-backed implementation is SpringTransactionBoundaryAdapter (infrastructure/adapter/out/tx, optional PlatformTransactionManager so unit tests run the action directly). Persistence failure *types* (spring-dao, e.g. OptimisticLockingFailureException) stay shared: they are the failure taxonomy mapped centrally by the problem handlers, not transaction control.

**Rule: no class under com.bank.app.account.., com.bank.app.transfer.., com.bank.app.user.. or com.bank.app.audit.. may access TransactionSynchronizationManager, TransactionSynchronization, TransactionTemplate, PlatformTransactionManager or TransactionDefinition** — enforced by CodingRulesArchitectureTest.noProgrammaticSpringTransactionsInBoundedContexts.
