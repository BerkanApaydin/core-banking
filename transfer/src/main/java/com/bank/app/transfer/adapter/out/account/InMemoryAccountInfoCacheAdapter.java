package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AbstractAccountSnapshotCache;
import com.bank.app.accountapi.AccountSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-free in-memory fallback for the account snapshot cache.
 *
 * <p>Used by transfer unit tests and as {@code @ConditionalOnMissingBean}
 * fallback at runtime; production wires the infrastructure Caffeine-backed
 * adapter (primary) instead. Invalidation semantics live in
 * {@link AbstractAccountSnapshotCache}; this class is storage only.
 */
public class InMemoryAccountInfoCacheAdapter extends AbstractAccountSnapshotCache {

    private final ConcurrentHashMap<String, AccountSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<Long, String>> batches = new ConcurrentHashMap<>();

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
    protected Optional<Map<Long, String>> readBatch(String key) {
        return Optional.ofNullable(batches.get(key));
    }

    @Override
    protected void writeBatch(String key, Map<Long, String> batch) {
        batches.put(key, batch);
    }

    @Override
    protected void clearStorage() {
        snapshots.clear();
        batches.clear();
    }
}
