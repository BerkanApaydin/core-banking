package com.bank.app.account.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared base for {@link AccountSnapshotCache} backends.
 *
 * <p>Owns canonical keys, per-id IBAN mappings and granular invalidation.
 * Bulk reads fan out to one mapping per account id — never a combinatorial
 * key over the whole id set — so repeated pages share entries instead of
 * minting a distinct cache key per page combination.
 *
 * <p>Threading: single-key reads and writes delegate straight to the backend
 * ({@code ConcurrentHashMap}, Caffeine and Redis are all thread-safe for
 * single keys), so reads never block each other and the hot read path never
 * takes a lock.
 *
 * <p>The two <em>compound</em> operations must not interleave for the same
 * account: {@link #putByIban} writes a snapshot and registers its reverse
 * index, {@link #evictById} removes a snapshot across the id, IBAN and
 * mapping namespaces. If they overlapped, an eviction could drop the index
 * entry while the write was still registering it, stranding an IBAN snapshot
 * that no later eviction can reach.
 *
 * <p>They therefore take a per-account lock, striped over a small fixed
 * array ({@link #LOCK_STRIPES}) instead of the instance monitor. An
 * instance-wide monitor is correct but wrong in two ways: it serializes
 * every account's writes behind one monitor (unrelated accounts never
 * overlap in practice, so that is pure added latency on the mutation path),
 * and it lets a long {@link #evictAll()} region clear block the write path
 * for its whole duration. Striping keeps unrelated accounts independent;
 * two accounts that happen to share a stripe contend only for the
 * microseconds a compound operation holds it.
 *
 * <p>{@link #evictAll()} takes every stripe in a fixed order, so it acts as
 * a global barrier that no per-account operation can interleave with.
 * Compound paths that touch several accounts ({@link #putIbans},
 * {@link #evictAll}) acquire unique stripes in ascending index order and
 * release them in reverse, so every multi-lock path uses the same order and
 * the acquisition cannot deadlock.
 *
 * <p>What the locks deliberately do <em>not</em> cover: the read-through
 * race between a database read and the cache write that follows it. That
 * race lives above this class — the database read happens outside any cache
 * lock — and is documented in docs/account-snapshot-cache.md. No lock inside
 * the cache can close it.
 */
public abstract class AbstractAccountSnapshotCache implements AccountSnapshotCache {

    protected abstract Optional<AccountSnapshot> readSnapshot(String key);

    protected abstract void writeSnapshot(String key, AccountSnapshot snapshot);

    protected abstract void removeSnapshot(String key);

    /** Single id-to-IBAN mapping (one key per account id). */
    protected abstract Optional<String> readIbanMapping(Long accountId);

    protected abstract void writeIbanMapping(Long accountId, String iban);

    protected abstract void removeIbanMapping(Long accountId);

    /**
     * Clears snapshots AND id-to-IBAN mappings. Backends that share one
     * storage region for both satisfy this with a single clear.
     */
    protected abstract void clearStorage();

    /**
     * Reverse index: account id to live {@code "iban-*"} snapshot keys.
     * Makes {@link #evictById} O(1) instead of an O(n) storage scan per
     * balance mutation. Entries are tiny (one IBAN key string per cached
     * account) and bounded by distinct cached accounts; TTL-expired snapshots
     * leave harmless dangling keys that the next eviction of that account
     * removes. Cleared wholesale by {@link #evictAll()}.
     */
    private final ConcurrentHashMap<Long, Set<String>> ibanKeysByAccount = new ConcurrentHashMap<>();

    /**
     * Number of account-lock stripes. A power of two so the stripe index is a
     * cheap mask; 64 keeps false sharing between unrelated accounts rare
     * while bounding the overhead to 64 lock objects per cache instance.
     */
    private static final int LOCK_STRIPES = 64;

    private final ReentrantLock[] accountLocks = createAccountLocks();

    private static ReentrantLock[] createAccountLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    /**
     * Lock guarding the compound operations of one account. The id is never
     * null here: {@code evictById} rejects it up front and
     * {@code AccountSnapshot} requires a non-null id.
     */
    private ReentrantLock lockFor(Long accountId) {
        return accountLocks[stripeIndex(accountId)];
    }

    private int stripeIndex(Long accountId) {
        return Math.floorMod(accountId.hashCode(), accountLocks.length);
    }

    @Override
    public Optional<AccountSnapshot> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return readSnapshot("id-" + accountId);
    }

    @Override
    public void putById(Long accountId, AccountSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        // Same stripe as evictById(accountId) -> a snapshot write and the
        // eviction of that account are mutually exclusive.
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            writeSnapshot("id-" + accountId, snapshot);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<AccountSnapshot> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return readSnapshot("iban-" + AccountSnapshotCache.ibanKey(ibanValue));
    }

    @Override
    public void putByIban(String ibanValue, AccountSnapshot snapshot) {
        if (ibanValue == null || snapshot == null) {
            return;
        }
        // Same stripe as evictById(snapshot.id()) -> the write and the
        // eviction of one account are mutually exclusive.
        ReentrantLock lock = lockFor(snapshot.id());
        lock.lock();
        try {
            String key = AccountSnapshotCache.ibanKey(ibanValue);
            writeSnapshot("iban-" + key, snapshot);
            if (snapshot.id() != null) {
                ibanKeysByAccount.computeIfAbsent(snapshot.id(), id -> ConcurrentHashMap.newKeySet())
                        .add("iban-" + key);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Optional.empty();
        }
        // All-or-nothing: a partially cached set is a miss, so callers fall
        // back to one batched DB read instead of N single reads. A null
        // member counts as a miss (previously sorting threw on it).
        if (accountIds.stream().anyMatch(id -> id == null)) {
            return Optional.empty();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(accountIds)) {
            Optional<String> iban = readIbanMapping(id);
            if (iban.isEmpty() || iban.get().isEmpty()) {
                return Optional.empty();
            }
            result.put(id, iban.get());
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(result));
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        // Map keys are authoritative; the collection only guards the null contract.
        Map<Long, String> writes = new LinkedHashMap<>();
        ibans.forEach((id, iban) -> {
            if (id != null && iban != null && !iban.isEmpty()) {
                writes.put(id, iban);
            }
        });
        if (writes.isEmpty()) {
            return;
        }
        // Hold every affected stripe so an eviction of any of these accounts
        // cannot interleave with this batch. Unique stripes are acquired in
        // ascending order and released in reverse, matching evictAll's fixed
        // order and preventing deadlock between concurrent batch writes.
        int[] stripes = writes.keySet().stream()
                .mapToInt(this::stripeIndex)
                .distinct()
                .sorted()
                .toArray();
        int acquired = 0;
        try {
            for (int stripe : stripes) {
                accountLocks[stripe].lock();
                acquired++;
            }
            writes.forEach(this::writeIbanMapping);
        } finally {
            // Release only the stripes actually acquired: an acquisition that
            // fails mid-loop must not leave the already-taken stripes locked.
            for (int i = acquired - 1; i >= 0; i--) {
                accountLocks[stripes[i]].unlock();
            }
        }
    }

    @Override
    public void evictAll() {
        // Global barrier: every stripe is held (fixed ascending order) so no
        // per-account or multi-account operation can interleave with the region
        // clear. Multi-stripe paths share this order, so no deadlock.
        int acquired = 0;
        try {
            for (ReentrantLock lock : accountLocks) {
                lock.lock();
                acquired++;
            }
            clearStorage();
            ibanKeysByAccount.clear();
        } finally {
            // Release only the stripes this thread actually acquired: if an
            // acquisition fails mid-loop, the ones already taken must not
            // stay locked forever.
            for (int i = acquired - 1; i >= 0; i--) {
                accountLocks[i].unlock();
            }
        }
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            removeSnapshot("id-" + accountId);
            Set<String> ibanKeys = ibanKeysByAccount.remove(accountId);
            if (ibanKeys != null) {
                ibanKeys.forEach(this::removeSnapshot);
            }
            removeIbanMapping(accountId);
        } finally {
            lock.unlock();
        }
    }
}
