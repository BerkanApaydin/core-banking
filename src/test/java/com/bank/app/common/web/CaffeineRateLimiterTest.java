package com.bank.app.common.web;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineRateLimiterTest {

    @Test
    void shouldAllowRequestsUntilLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(3, 10_000);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldRejectWhenLimitExceeded() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 10_000);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));

        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldUseSeparateCountersForDifferentClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 10_000);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));

        assertFalse(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-2"));
    }

    @Test
    void shouldCreateNewCounterWhenEntryExpired() throws Exception {
        // küçük window veriyoruz ki expire kesin olsun
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 50);

        String client = "client";

        // ilk window
        assertTrue(limiter.tryAcquire(client));
        assertTrue(limiter.tryAcquire(client));

        // expire olması için bekle
        Thread.sleep(100);

        // yeni window başlamış olmalı (counter reset)
        assertTrue(limiter.tryAcquire(client));
        assertTrue(limiter.tryAcquire(client));
        assertFalse(limiter.tryAcquire(client));
    }

    @Test
    void rateLimitInfoShouldReportNotExpired() throws Exception {
        Class<?> clazz = Class.forName(
                "com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");

        Constructor<?> constructor = clazz.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);

        Object info = constructor.newInstance(1, 10_000);

        Method isExpiredMethod = clazz.getDeclaredMethod("isExpired");
        isExpiredMethod.setAccessible(true);

        boolean expired = (boolean) isExpiredMethod.invoke(info);

        assertFalse(expired);
    }

    @Test
    void rateLimitInfoShouldReportExpired() throws Exception {
        Class<?> clazz = Class.forName(
                "com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");

        Constructor<?> constructor = clazz.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);

        Object info = constructor.newInstance(1, 10_000);

        Field resetTimeField = clazz.getDeclaredField("resetTime");
        resetTimeField.setAccessible(true);

        resetTimeField.setLong(info,
                System.currentTimeMillis() - 1000);

        Method isExpiredMethod = clazz.getDeclaredMethod("isExpired");
        isExpiredMethod.setAccessible(true);

        boolean expired = (boolean) isExpiredMethod.invoke(info);

        assertTrue(expired);
    }

    @Test
    void rateLimitInfoConstructorShouldInitializeFields() throws Exception {
        Class<?> clazz = Class.forName(
                "com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");

        Constructor<?> constructor = clazz.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);

        Object info = constructor.newInstance(5, 10_000);

        Field requestCountField = clazz.getDeclaredField("requestCount");
        requestCountField.setAccessible(true);

        AtomicInteger requestCount = (AtomicInteger) requestCountField.get(info);

        assertEquals(5, requestCount.get());

        Field resetTimeField = clazz.getDeclaredField("resetTime");
        resetTimeField.setAccessible(true);

        long resetTime = resetTimeField.getLong(info);

        assertTrue(resetTime > System.currentTimeMillis());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateNewEntryWhenExistingEntryIsExpired() throws Exception {

        CaffeineRateLimiter limiter = new CaffeineRateLimiter(10, 60_000);

        Field cacheField = CaffeineRateLimiter.class.getDeclaredField("cache");

        cacheField.setAccessible(true);

        Cache<String, Object> cache = (Cache<String, Object>) cacheField.get(limiter);

        Class<?> rateLimitInfoClass = Class.forName(
                "com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");

        Constructor<?> constructor = rateLimitInfoClass.getDeclaredConstructor(
                int.class,
                long.class);

        constructor.setAccessible(true);

        Object expiredInfo = constructor.newInstance(
                5,
                -1000L);

        cache.put("client1", expiredInfo);

        boolean result = limiter.tryAcquire("client1");

        assertTrue(result);
    }

}