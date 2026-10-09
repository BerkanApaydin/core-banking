package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AbstractAccountSnapshotCache;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.infrastructure.adapter.in.config.SnapshotCacheCaffeineCondition;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Caffeine-backed account snapshot cache implementation (single-JVM).
 *
 * <p>Active when the resolved snapshot-cache backend is single-JVM (the
 * default; see
 * {@link com.bank.app.infrastructure.adapter.in.config.CacheBackendResolution}).
 * Production uses the Redis backend
 * instead so that all replicas share one cache and invalidations are
 * visible cluster-wide. Takes precedence over the transfer module's in-memory
 * fallback via {@code @Primary}. Invalidation semantics live in
 * {@link AbstractAccountSnapshotCache}; this class is storage only. Snapshots
 * and id-to-IBAN mappings share the single region, so one clear drops both.
 */
@Component
@Primary
@Conditional(SnapshotCacheCaffeineCondition.class)
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
    protected Optional<String> readIbanMapping(Long accountId) {
        return Optional.ofNullable(cache().get(ibanMappingKey(accountId), String.class));
    }

    @Override
    protected void writeIbanMapping(Long accountId, String iban) {
        cache().put(ibanMappingKey(accountId), iban);
    }

    @Override
    protected void removeIbanMapping(Long accountId) {
        cache().evict(ibanMappingKey(accountId));
    }

    private static String ibanMappingKey(Long accountId) {
        return "iban-of-" + accountId;
    }

    @Override
    protected void clearStorage() {
        cache().clear();
    }
}
