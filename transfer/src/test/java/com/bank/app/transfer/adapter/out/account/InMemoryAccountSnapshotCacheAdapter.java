package com.bank.app.transfer.adapter.out.account;

import com.bank.app.account.api.AbstractAccountSnapshotCache;
import com.bank.app.account.api.AccountSnapshot;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-free in-memory snapshot cache: a TEST DOUBLE, not a runtime
 * backend.
 *
 * <p>It lives in the test sources on purpose. The runtime backend is
 * always supplied by the {@code infrastructure} module — the Caffeine-backed
 * adapter for dev/test, the Redis-backed one for production — so there is
 * no {@code @ConditionalOnMissingBean} fallback bean that could activate a
 * per-JVM map in a multi-replica deployment. Removing that fallback made
 * the "production must never serve stale snapshots from one pod's heap"
 * invariant structural instead of conditional: the per-JVM implementation
 * simply is not on the production classpath.
 *
 * <p>Invalidation semantics live in {@link AbstractAccountSnapshotCache};
 * this class is storage only.
 */
public class InMemoryAccountSnapshotCacheAdapter extends AbstractAccountSnapshotCache {

    private final ConcurrentHashMap<String, AccountSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> ibanMappings = new ConcurrentHashMap<>();

    @Override
    protected Optional<AccountSnapshot> readSnapshot(String key) {
        return Optional.ofNullable(snapshots.get(key));
    }

    @Override
    protected void writeSnapshot(String key, AccountSnapshot snapshot) {
        snapshots.put(key, snapshot);
    }

    @Override
    protected void removeSnapshot(String key) {
        snapshots.remove(key);
    }

    @Override
    protected Optional<String> readIbanMapping(Long accountId) {
        return Optional.ofNullable(ibanMappings.get(accountId));
    }

    @Override
    protected void writeIbanMapping(Long accountId, String iban) {
        ibanMappings.put(accountId, iban);
    }

    @Override
    protected void removeIbanMapping(Long accountId) {
        ibanMappings.remove(accountId);
    }

    @Override
    protected void clearStorage() {
        snapshots.clear();
        ibanMappings.clear();
    }
}
