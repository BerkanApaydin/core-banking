package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Exposes the resolved snapshot-cache settings (canonical-wins, see
 * {@link CacheBackendResolution}) as a single injectable value.
 *
 * <p>Adapters and {@link CacheConfig} must inject this bean — never
 * {@link CacheProperties} directly — so runtime behavior and the
 * {@code @Conditional} backend selection can never disagree.
 */
@Configuration
public class AccountCacheAliasConfig {

    @Bean
    public CacheProperties.AccountInfoCache resolvedAccountInfoCache(Environment environment) {
        return CacheBackendResolution.resolve(environment);
    }
}
