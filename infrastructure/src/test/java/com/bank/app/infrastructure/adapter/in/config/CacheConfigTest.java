package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheConfigTest {

    @Test
    void shouldRegisterAccountAclCache() {
        CacheProperties.AccountInfoCache settings =
                new CacheProperties.AccountInfoCache("caffeine", 1000, 60, 500);
        CacheConfig config = new CacheConfig(settings);

        assertNotNull(config.cacheManager().getCache("accountAclInfo"));
    }
}
