package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CachePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        CacheProperties props = new CacheProperties(null);
        CacheProperties.AccountInfoCache cache = props.accountInfo();

        assertEquals("caffeine", cache.backend());
        assertEquals(1000, cache.maximumSize());
        assertEquals(60, cache.expireAfterWrite());
        assertEquals(500, cache.evictionBatchSize());
    }

    @Test
    void shouldKeepCustomValues() {
        CacheProperties.AccountInfoCache cache =
                new CacheProperties.AccountInfoCache("redis", 5000, 120, 250);

        assertEquals("redis", cache.backend());
        assertEquals(5000, cache.maximumSize());
        assertEquals(120, cache.expireAfterWrite());
        assertEquals(250, cache.evictionBatchSize());
    }

    @Test
    void shouldFallBackToCaffeineOnBlankBackend() {
        CacheProperties.AccountInfoCache cache =
                new CacheProperties.AccountInfoCache("  ", 5000, 120, 500);

        assertEquals("caffeine", cache.backend());
    }
}
