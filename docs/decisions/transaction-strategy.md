# Transaction Strategy (5.1)

Two mechanisms coexist deliberately — this note records which to use where.

## Use cases: AOP programmatic transactions

`@TransactionalUseCase` / `@ReadOnlyUseCase` marker annotations are turned into
transactions by `UseCaseTransactionAspect` (programmatic
`PlatformTransactionManager` usage). The aspect centralizes three policies no
single `@Transactional` can express uniformly:

- explicit 30s timeout (`app.transaction.timeout-seconds`) so hung
  `PESSIMISTIC_WRITE` row locks cannot exhaust the Hikari pool;
- explicit `READ_COMMITTED` isolation (the locking strategy is pessimistic, so
  `REPEATABLE_READ`/`SERIALIZABLE` would only add 40001/40003 retries);
- `REQUIRES_NEW` for the `audit` package so `AFTER_COMMIT` observers never join
  the business transaction.

**Rule: application-layer code (`..application..`) must never use
`@Transactional`** — enforced by
`LayeringArchitectureTest.applicationLayerShouldNotDependOnSpringTransaction`.

## Adapters: declarative `@Transactional`

Single-boundary adapter methods (`OutboxProcessor`, `IdempotencyGuard`,
`*PersistenceAdapter`) use declarative `@Transactional` because each owns
exactly one transaction boundary with its own propagation (typically
`REQUIRES_NEW` for claim/checkpoint writes that must survive the caller's
rollback). Every such annotation carries `timeout = 30` to match the use-case
budget; read-only lookups stay bare `@Transactional(readOnly = true)`.

**Rule: never nest a declarative transaction inside `AdvisorySchedulerLock` —
the guard owns its transaction, and a contended lock marks rollback-only.**

## Why not one mechanism?

Moving use cases to `@Transactional` would scatter timeout/isolation policy
across 14+ call sites with no compile-time exhaustiveness. Moving adapters to
the aspect would force business-shaped markers (`@TransactionalUseCase`) onto
infrastructure concerns (outbox polling, idempotency claims) that are not use
cases. The split follows the layer, not the author.
