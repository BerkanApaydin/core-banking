package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CachePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        CacheProperties props = new CacheProperties();
        CacheProperties.AccountInfoCache cache = props.getAccountInfo();

        assertEquals(1000, cache.getMaximumSize());
        assertEquals(60, cache.getExpireAfterWrite());
    }

    @Test
    void shouldSetAndGetMaximumSize() {
        CacheProperties.AccountInfoCache cache = new CacheProperties.AccountInfoCache();
        cache.setMaximumSize(5000);
        assertEquals(5000, cache.getMaximumSize());
    }

    @Test
    void shouldSetAndGetExpireAfterWrite() {
        CacheProperties.AccountInfoCache cache = new CacheProperties.AccountInfoCache();
        cache.setExpireAfterWrite(120);
        assertEquals(120, cache.getExpireAfterWrite());
    }
}
