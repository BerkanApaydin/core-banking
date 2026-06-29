package com.bank.app.common.adapter.in.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    private final CacheProperties cacheProperties;

    public CacheConfig(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("accountInfo");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheProperties.getAccountInfo().getMaximumSize())
                .expireAfterWrite(cacheProperties.getAccountInfo().getExpireAfterWrite(), TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
}
