# Cross-BC Read Model (K17)

## Status

Documented direction; current snapshot-cache design is sufficient for today's
read needs.

## Today

`transfer` reads account state exclusively through the `account-api`
published language (`AccountApi` → `AccountApiAdapter`), with
`AccountSnapshotCache` (Caffeine dev / Redis prod) in front. The snapshot is
identity + status only (`AccountSnapshotContractTest` pins this) — balances
are always re-read authoritatively under pessimistic locks in the mutation
transaction, so the cache cannot weaken overdraft protection. Mutations evict
both legs granularly (`AccountAclAdapter.evictMutatedAccounts`); the 60s TTL
is a backstop, not the consistency mechanism. Full semantics:
`docs/account-snapshot-cache.md`.

## When this stops being enough

- History/report pages need account fields beyond id/userId/currency/status
  (owner name changes, per-account limits), or
- transfer-side reads need point-in-time consistency across pages (today each
  page is explicitly NOT a snapshot), or
- the Account context leaves the monolith (see `saga-readiness.md`).

## Direction then

Projection owned by `transfer`, fed by account domain events over the existing
outbox (`AccountCreatedEvent`, `AccountCreditedEvent`, `AccountDebitedEvent`
already publish through `AccountEventOutboxRelay`): a
`transfer.adapter.out.projection` applier upserts a local read table keyed by
account id. Ordering by outbox `created_at` per aggregate; last-write-wins on
the event's occurred-at. The snapshot cache stays as the hot path in front of
the projection — same eviction contract, richer payload.

Explicitly NOT chosen now: synchronous cross-service queries (couples
availability), shared tables (couples storage), or event-carried state beyond
the published language (breaks the ACL).
