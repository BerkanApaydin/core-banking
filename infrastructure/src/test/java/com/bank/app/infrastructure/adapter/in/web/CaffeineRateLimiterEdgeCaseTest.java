package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CaffeineRateLimiterEdgeCaseTest {

    @Test
    void shouldAllowRequestWhenUnderLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60000, 10000, Clock.systemUTC());
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldBlockRequestWhenOverLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 60000, 10000, Clock.systemUTC());
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 60000, 10000, Clock.systemUTC());
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));
        assertTrue(limiter.tryAcquire("client-3"));
    }

    @Test
    void shouldResetAfterWindowExpires() {
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 50000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        Clock laterClock = Clock.fixed(now.plusMillis(50001), ZoneOffset.UTC);
        CaffeineRateLimiter limiterWithLaterClock = new CaffeineRateLimiter(1, 50000, 10000, laterClock);
        assertTrue(limiterWithLaterClock.tryAcquire("client-1"));
    }

    @Test
    void shouldHandleNullClientKey() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60000, 10000, Clock.systemUTC());
        assertThrows(NullPointerException.class, () -> limiter.tryAcquire(null));
    }

    @Test
    void shouldHandleHighVolumeClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1000, 60000, 10000, Clock.systemUTC());
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire("high-volume"));
        }
        assertFalse(limiter.tryAcquire("high-volume"));
    }

    @Test
    void shouldHandleManyDistinctClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 60000, 10000, Clock.systemUTC());
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire("client-" + i));
        }
    }

    @Test
    void shouldExpirePastBoundary() {
        CaffeineRateLimiterTest.SettableClock clock = new CaffeineRateLimiterTest.SettableClock(0);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 50000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        clock.advance(50001);

        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldNotExpireAtExactBoundary() {
        CaffeineRateLimiterTest.SettableClock clock = new CaffeineRateLimiterTest.SettableClock(0);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 50000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-1"));

        clock.advance(50000);

        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldIsolateTiersPerBudget() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 60000, 10000, Clock.systemUTC());

        assertTrue(limiter.tryAcquire("tiered"));
        assertTrue(limiter.tryAcquire("tiered"));
        assertFalse(limiter.tryAcquire("tiered"));
        // Same key under a looser tier still has budget: auth abuse must not
        // eat the resource budget.
        assertTrue(limiter.tryAcquire("tiered", 120, 60_000));
        // And the exhausted tight tier stays exhausted.
        assertFalse(limiter.tryAcquire("tiered", 2, 60_000));
    }
}
