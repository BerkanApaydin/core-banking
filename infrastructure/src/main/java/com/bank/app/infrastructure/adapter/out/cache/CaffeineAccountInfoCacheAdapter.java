package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AbstractAccountSnapshotCache;
import com.bank.app.accountapi.AccountSnapshot;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Caffeine-backed account snapshot cache implementation.
 *
 * <p>Reuses the {@code accountAclInfo} cache region defined in
 * {@code CacheConfig}. Takes precedence over the transfer module's in-memory
 * fallback via {@code @Primary}. Invalidation semantics live in
 * {@link AbstractAccountSnapshotCache}; this class is storage only.
 */
@Component
@Primary
public class CaffeineAccountInfoCacheAdapter extends AbstractAccountSnapshotCache {

    private static final String CACHE_NAME = "accountAclInfo";

    private final CacheManager cacheManager;

    public CaffeineAccountInfoCacheAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CACHE_NAME);
        }
        return cache;
    }

    @Override
    protected Optional<AccountSnapshot> readSnapshot(String key) {
        return Optional.ofNullable(cache().get(key, AccountSnapshot.class));
    }

    @Override
    protected void writeSnapshot(String key, AccountSnapshot snapshot) {
        cache().put(key, snapshot);
    }

    @Override
    protected void removeSnapshot(String key) {
        cache().evict(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<Map<Long, String>> readBatch(String key) {
        return Optional.ofNullable(cache().get(key, Map.class));
    }

    @Override
    protected void writeBatch(String key, Map<Long, String> batch) {
        cache().put(key, batch);
    }

    @Override
    protected void clearStorage() {
        cache().clear();
    }
}
