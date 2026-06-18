package com.bank.app.common.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineRateLimiterEdgeCaseTest {

    @Test
    void shouldAllowRequestWhenUnderLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60000, 10000, Clock.systemDefaultZone());
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldBlockRequestWhenOverLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 60000, 10000, Clock.systemDefaultZone());
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 60000, 10000, Clock.systemDefaultZone());
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));
        assertTrue(limiter.tryAcquire("client-3"));
    }

    @Test
    void shouldResetAfterWindowExpires() {
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneId.systemDefault());
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 50000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        Clock laterClock = Clock.fixed(now.plusMillis(50001), ZoneId.systemDefault());
        CaffeineRateLimiter limiterWithLaterClock = new CaffeineRateLimiter(1, 50000, 10000, laterClock);
        assertTrue(limiterWithLaterClock.tryAcquire("client-1"));
    }

    @Test
    void shouldHandleNullClientKey() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60000, 10000, Clock.systemDefaultZone());
        assertThrows(NullPointerException.class, () -> limiter.tryAcquire(null));
    }

    @Test
    void shouldHandleHighVolumeClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1000, 60000, 10000, Clock.systemDefaultZone());
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire("high-volume"));
        }
        assertFalse(limiter.tryAcquire("high-volume"));
    }

    @Test
    void shouldHandleManyDistinctClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 60000, 10000, Clock.systemDefaultZone());
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire("client-" + i));
        }
    }
}
