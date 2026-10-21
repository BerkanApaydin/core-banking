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
| `transfer` → `debitAndCredit` / `reverseForCancellation` (via `AccountApiAdapter`) | `AccountApiAdapter.evictSnapshotsAfterCommit` evicts **both legs by id** after commit (granular; unrelated entries never stampede). Ownership lives on the Account boundary — `AccountAclAdapter` must not double-evict (see its class javadoc) |
| Account open (`POST /accounts`) | Nothing to evict: the id is new, and the per-id IBAN map only gains entries |
| Account suspend | `SuspendAccountUseCaseImpl` defers the id eviction to after commit (`TransactionBoundaryPort.runAfterCommit`, same pattern as `AccountApiAdapter`); status reads fall back to DB on TTL expiry |

**Eviction always happens after the commit, never inside the transaction.**
Evicting before the commit opens a window: a concurrent read-through can
read the pre-commit (still `ACTIVE`) row and write it back into the cache
*after* the eviction, and that stale entry then survives for the whole TTL.
Deferring the eviction to `AFTER_COMMIT` shrinks the window from "the
transaction duration" to "the eviction round trip" and is pinned by
`CacheInvalidationArchitectureTest.accountStatusMutatorsMustDeferEvictionToAfterCommit`.

## The residual read-through race (known, bounded, and not a decision input)

The cache-aside read-through is

```
cache.get(id) → miss → database read → cache.put(id, value)
```

The database read happens *outside* the cache, so no lock inside the cache
can order it against a concurrent eviction. A reader that read the row
before a mutation committed can still write its stale value back after the
eviction, and that entry then lives for the TTL (60 s by default).

Properties of this race in this codebase:

- **Bounded.** The window is the duration of one projection query plus one
  cache write — milliseconds, not the TTL. Every subsequent read of that
  account sees the fresh value.
- **Not a decision input today.** The only mutable field in the snapshot is
  `status`, and nothing in the transfer context reads it. The
  authorization decision is made by `Account.debit()` on the aggregate
  re-read under `PESSIMISTIC_WRITE`, so a stale `ACTIVE` snapshot can
  never authorize a suspended account. Pinned by
  `AccountTest.Debit.shouldThrowOnSuspendedAccount` (the aggregate rejects
  the movement) and `PlaceTransferUseCaseImplTest.FailureHandling.
  shouldNotCompensateOnDebitFailure` (a cached `ACTIVE` lookup followed by a
  failed mutation propagates and never completes the transfer).
- **Would need a fencing token to close.** The industry-standard fix is a
  monotonic revision on the snapshot plus a compare-and-set write (the
  cache rejects a write whose revision is older than the last invalidation
  for that account). That requires either a new component on the published
  `AccountSnapshot` (currently pinned to `id|userId|currency|status` by
  `AccountSnapshotContractTest`) or moving the read-through into the cache
  so the cache owns the token. It is deliberately not implemented: the
  contract test pins the published shape on purpose, and the field the race
  could affect is not read for any decision. Revisit it if a consumer
  starts branching on the cached status.

## Locking inside the cache

`AbstractAccountSnapshotCache` guards the two *compound* operations —
`putByIban` (snapshot write + reverse-index registration) and `evictById`
(removal across the id, IBAN and mapping namespaces) — with a
**per-account lock, striped over 64 slots**. A single instance-wide monitor
would also be correct, but it serializes every account's writes behind one
monitor and lets a long `evictAll()` region clear block the write path for
its whole duration. Reads and single-key writes take no lock at all.

The Redis backend needs no Java-level lock for the same reason: each
mutation is a single Lua script, and Redis executes scripts atomically.

## Capacity: `maximum-size` applies to Caffeine only

`app.cache.account-info.maximum-size` caps the entry count of the
**Caffeine** backend. The Redis backend has no entry-count cap: its region
is bounded by the TTL instead, so its memory is roughly
`write rate x expire-after-write`. Size the broker with `maxmemory` and a
`maxmemory-policy`, not with this key. The application logs this once at
startup in prod so the difference is never a surprise.

Cross-replica: the Redis backend keeps the id↔IBAN index in Redis too
(sets + reverse keys, same TTL), so an `evictById` on one pod also drops
IBAN entries written by other pods. The Caffeine backend is single-JVM
(dev/test/single instance) — never use it with >1 replica.

## Failure mode: fail-open (reads) / fail-loud (evictions)

Every Redis call is guarded: on outage, reads return `Optional.empty()`
(callers fall back to the DB) and writes become no-ops. A cache outage
costs extra DB load, never a 500.

**Eviction is the exception.** It is the only thing standing between a
committed mutation and a stale snapshot, so a failed eviction is retried
(bounded, 3 attempts) and, if it still fails, logged at `ERROR` with the
account id — the request still succeeds, but the loss is visible instead
of hiding in a `WARN`. Eviction loss is otherwise self-healing via the TTL.

## Rules for future changes

1. **Never add balance (or any mutable ledger state) to `AccountSnapshot`.**
   The transfer decision path (`authorizeSender` → `debitAndCredit`) re-reads
   authoritative state under pessimistic locks; a cached balance would bypass
   overdraft protection.
2. **Every new balance-mutation path must evict its accounts** — follow
   `AccountApiAdapter.evictSnapshotsAfterCommit` (by id after commit, not `evictAll`).
   Never evict before commit: a concurrent read-through could repopulate the
   cache from pre-commit state and the stale snapshot would survive the
   eviction for the full TTL.
   Pinned by `CacheInvalidationArchitectureTest.accountApiMutationsMustEvictSnapshots`
   (new `AccountApi` mutators), `.accountStatusMutatorsMustEvictSnapshots`
   (status mutators) and `.accountStatusMutatorsMustDeferEvictionToAfterCommit`
   (status mutators must route eviction through `runAfterCommit`).
3. Keep index keys on the same TTL as data keys (see `SnapshotKeys`).
4. **A new backend must implement `evictById` explicitly.** The port default
   throws `UnsupportedOperationException` on purpose: the previous default
   delegated to `evictAll()`, which silently wiped every cached account —
   a database stampede whose blast radius grows with the account count.

## Which backend runs, and why the in-memory one never should

The cache port has three implementations. Which one is wired is decided by the
resolved backend (canonical `app.cache.account-info.backend`, legacy
`app.cache.caffeine.account-info.backend` as fallback — canonical wins on
conflict; see `CacheBackendResolution`):

| Value | Bean | Where |
|---|---|---|
| `redis` (production) | `RedisAccountSnapshotCacheAdapter` | `SnapshotCacheRedisCondition` (+ `RedisRateLimitConfiguration.SnapshotCacheRedis` for the connection factory) |
| `caffeine` (dev/test, single JVM) | `CaffeineAccountInfoCacheAdapter` | `SnapshotCacheCaffeineCondition` |
| anything else | **nothing registers** — startup fails | `ApplicationStartupValidator` rejects the unknown value |

`application-prod.yml` sets both keys to the **literal** `redis` (not
`${CACHE_ACCOUNT_INFO_BACKEND:redis}`). That matters: a literal cannot be
redirected by an environment variable, so the fallback cannot be switched on
in production by accident. The base `application.yml` deliberately keeps the
`CACHE_ACCOUNT_INFO_*` placeholders on the alias prefix and leaves the
canonical keys unset, so a legacy-only deployment still resolves through the
fallback; do not add canonical defaults to the base file — with
presence-based resolution every deployment would become canonical and the
fallback would silently die.

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
refuses to start at all under the `prod` or `simulation` profiles
(`IllegalStateException`, pinned by `TransferBeanConfigTest`). Either signal
fires before a single stale snapshot is served.

A **typo in the backend value** (`rediss`, `redis2`) is a third tripwire: it
used to select the Caffeine backend silently, which is the one failure mode
nobody can see in the logs. Unknown values are now rejected at startup in
*every* profile, and the backend conditions match exactly (`caffeine` /
`redis`) instead of "anything that is not redis".

The fallback itself is still correct and intentional for dev, test and
single-JVM use, where it removes a Redis dependency without changing
semantics — `AbstractAccountSnapshotCache` is shared by all three backends, so
the invalidation contract is identical either way.
