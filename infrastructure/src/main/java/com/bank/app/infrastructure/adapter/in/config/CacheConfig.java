package com.bank.app.infrastructure.adapter.in.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// No @EnableCaching on purpose: nothing in this codebase uses @Cacheable —
// caching is manual through the AccountSnapshotCache port, and the CacheManager
// below exists only as its Caffeine backend. Enabling the caching advisor
// would add a proxy to every bean for zero benefit.
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    private final CacheProperties cacheProperties;

    public CacheConfig(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        // Single region: "accountAclInfo" serves the transfer-facing ACL adapter
        // through AccountInfoCachePort (infrastructure owns the backend).
        // Nothing uses @Cacheable in this codebase — manual port-based caching
        // only — so no other region is pre-created.
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("accountAclInfo");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheProperties.accountInfo().maximumSize())
                .expireAfterWrite(cacheProperties.accountInfo().expireAfterWrite(), TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
}
