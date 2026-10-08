# T-13: Account Balance Locking (Pessimistic Read + Optimistic Write)

**Decision:** balance mutation keeps BOTH mechanisms, each with a defined job —
they are not two alternatives accidentally left on.

1. **Pessimistic read** — `AdjustAccountBalancesUseCaseImpl.loadOrderedPair`
   locks both rows with `SELECT ... FOR UPDATE` in stable ID order
   (`OrderedPair`), so concurrent transfers over the same pair can neither
   deadlock nor lost-update inside one local transaction.
2. **Optimistic versioned write** — `AccountPersistenceAdapter.save` uses the
   bulk `updateIfVersionMatch` path (no SELECT on the happy path, 2 SELECTs
   saved per transfer). The `@Version` check is a cross-check, not the primary
   guard: a row locked in step 1 cannot change version before commit, so a
   version conflict on this path means either a code-path bug (write without
   prior lock) or a legacy version-less aggregate — both worth a 409, never a
   silent overwrite.
3. **Transfer status** stays purely optimistic (`updateStatusIfVersionMatch`):
   status transitions are single-row, lock-free, retried by
   `TransferUseCaseRetryAspect`.

**Why not pessimistic-only (drop `@Version`)?** The version column costs one
comparison per write and buys: (a) a fail-loud tripwire if a future code path
writes without locking, (b) the 409 contract the API already exposes
(`OPTIMISTIC_LOCK_CONFLICT`, alert `OptimisticLockConflictsRising`), (c) the
reaper's conflict counting. Removing it saves nothing measurable.

**Why not optimistic-only (drop `FOR UPDATE`)?** Balances are read-modify-write
with a domain invariant (`debit` insufficient-funds check) computed in Java.
Pure optimistic would turn every same-pair collision into a 409 + client
retry; the ordered lock serializes the pair inside the DB where the wait is
cheap and the retry logic stays out of the client contract.

**Do not change one side without re-reading this file:** dropping the lock
reintroduces lost updates under contention; dropping the version removes the
tripwire and breaks the 409/alerting contract.
