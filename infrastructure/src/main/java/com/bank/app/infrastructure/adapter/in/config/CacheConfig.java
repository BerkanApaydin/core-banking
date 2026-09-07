package com.bank.app.infrastructure.adapter.in.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
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
                .maximumSize(cacheProperties.getAccountInfo().getMaximumSize())
                .expireAfterWrite(cacheProperties.getAccountInfo().getExpireAfterWrite(), TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
}
