package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AbstractAccountSnapshotCache;
import com.bank.app.accountapi.AccountSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Caffeine-backed account snapshot cache implementation (single-JVM).
 *
 * <p>Active when {@code app.cache.caffeine.account-info.backend=caffeine}
 * (default: dev/test/single instance). Production uses the Redis backend
 * instead so that all replicas share one cache and invalidations are
 * visible cluster-wide. Takes precedence over the transfer module's in-memory
 * fallback via {@code @Primary}. Invalidation semantics live in
 * {@link AbstractAccountSnapshotCache}; this class is storage only.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.cache.caffeine.account-info.backend", havingValue = "caffeine", matchIfMissing = true)
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
