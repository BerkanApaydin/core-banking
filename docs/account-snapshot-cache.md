# Account Snapshot Cache — Semantics (14.1 / K10)

## What is cached

`AccountSnapshot(id, userId, currency, status)` — identity and status only.
**Balances are never cached.** Every balance shown to users or used in a
transfer decision is read from PostgreSQL inside the mutation's own
transaction (`AdjustAccountBalancesUseCaseImpl` → `findByIdForUpdate`).
A 60-second TTL therefore cannot show a stale balance, only a stale
currency/status — and those change rarely (suspend/close).

Contract guard: `AccountSnapshotContractTest` pins the record shape; adding a
`balance` component breaks the build by design.

## Invalidation (the consistency mechanism — TTL is only the backstop)

| Writer path | Invalidation |
|---|---|
| `transfer` → `debitAndCredit` / `reverseForCancellation` | `AccountAclAdapter.evictMutatedAccounts` evicts **both legs by id** (granular; unrelated entries never stampede) |
| Account open (`POST /accounts`) | Nothing to evict: the id is new, and the per-id IBAN map only gains entries |
| Account suspend/close | Same transfer-path eviction on next mutation; status reads fall back to DB on TTL expiry |

Cross-replica: the Redis backend keeps the id↔IBAN index in Redis too
(sets + reverse keys, same TTL), so an `evictById` on one pod drops IBAN
entries written by other pods. The Caffeine backend is single-JVM
(dev/test/single instance) — never use it with >1 replica.

## Failure mode: fail-open

Every Redis call is guarded: on outage, reads return `Optional.empty()`
(callers fall back to the DB) and writes/evicts become no-ops. A cache outage
costs extra DB load, never a 500. Eviction loss is self-healing via TTL.

## Rules for future changes

1. **Never add balance (or any mutable ledger state) to `AccountSnapshot`.**
   The transfer decision path (`authorizeSender` → `debitAndCredit`) re-reads
   authoritative state under pessimistic locks; a cached balance would bypass
   overdraft protection.
2. **Every new balance-mutation path must evict its accounts** — follow
   `AccountAclAdapter.evictMutatedAccounts` (by id, not `evictAll`).
3. Keep index keys on the same TTL as data keys (see `SnapshotKeys`).

## Which backend runs, and why the in-memory one never should

The cache port has three implementations. Which one is wired is decided by a
single property, `app.cache.caffeine.account-info.backend`:

| Value | Bean | Where |
|---|---|---|
| `redis` (production) | `RedisAccountSnapshotCacheAdapter` | `RedisRateLimitConfiguration.SnapshotCacheRedis` |
| anything else | none registered → `TransferBeanConfig.accountInfoCachePort()` falls back to `InMemoryAccountInfoCacheAdapter` | `@ConditionalOnMissingBean(AccountSnapshotCache.class)` |
| — | `AbstractAccountSnapshotCache` | shared invalidation logic, not a backend |

`application-prod.yml` sets the property to the **literal** `redis` (not
`${CACHE_ACCOUNT_INFO_BACKEND:redis}` as the base file does). That matters: a
literal cannot be redirected by an environment variable, so the fallback
cannot be switched on in production by accident.

The silent-failure path this closes: if the prod value ever degrades away from
`redis`, no backend bean registers, the `@ConditionalOnMissingBean` fallback
quietly takes over, and a per-JVM `ConcurrentHashMap` starts serving all
replicas. With HPA at up to six pods, an eviction on pod A never reaches pod B,
so account status and currency reads go stale cluster-wide for up to the TTL —
with nothing in the logs to indicate it. `ProductionConfigContractTest`
(`app/src/test/java/com/bank/app/security/`) asserts both the literal value and
the absence of a `${...}` placeholder, so this cannot regress silently.

Two additional tripwires sit directly on the fallback: it logs a `WARN` on every
activation (a per-JVM cache serving traffic is always worth a log line), and it
refuses to start at all under the `prod` profile (`IllegalStateException`,
pinned by `TransferBeanConfigTest`). Either signal fires before a single stale
snapshot is served.

The fallback itself is still correct and intentional for dev, test and
single-JVM use, where it removes a Redis dependency without changing
semantics — `AbstractAccountSnapshotCache` is shared by all three backends, so
the invalidation contract is identical either way.
