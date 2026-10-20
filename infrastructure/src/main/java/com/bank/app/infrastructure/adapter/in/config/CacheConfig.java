package com.bank.app.infrastructure.adapter.in.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// No @EnableCaching on purpose: nothing in this codebase uses @Cacheable —
// caching is manual through the AccountSnapshotCache port, and the CacheManager
// below exists only as its Caffeine backend. Enabling the caching advisor
// would add a proxy to every bean for zero benefit.
//
// The CacheManager is created ONLY for the Caffeine backend. With the Redis
// backend (production) it was still allocated on every pod — an unused
// cache-sized manager per replica. The condition resolves BOTH prefixes
// (canonical + legacy alias) through CacheBackendResolution, exactly like the
// adapter conditions: a legacy-only deployment that selects Redis must not
// also get this Caffeine manager.
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@Conditional(SnapshotCacheCaffeineCondition.class)
public class CacheConfig {

    private final CacheProperties.AccountInfoCache accountInfoCache;

    public CacheConfig(CacheProperties.AccountInfoCache resolvedAccountInfoCache) {
        this.accountInfoCache = resolvedAccountInfoCache;
    }

    @Bean
    public CacheManager cacheManager() {
        // Single region: "accountAclInfo" serves the transfer-facing ACL adapter
        // through AccountInfoCachePort (infrastructure owns the backend).
        // Nothing uses @Cacheable in this codebase — manual port-based caching
        // only — so no other region is pre-created.
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("accountAclInfo");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(accountInfoCache.maximumSize())
                .expireAfterWrite(accountInfoCache.expireAfterWrite(), TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
}
