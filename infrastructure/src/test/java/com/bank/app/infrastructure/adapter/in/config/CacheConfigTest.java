package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheConfigTest {

    @Test
    void shouldRegisterAccountAclCache() {
        CacheProperties properties = new CacheProperties();
        CacheConfig config = new CacheConfig(properties);

        assertNotNull(config.cacheManager().getCache("accountAclInfo"));
    }
}
