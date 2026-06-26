package com.bank.app.common.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CaffeineRateLimiterTest {

    private final Clock clock = Clock.systemUTC();

    @Test
    void shouldAllowRequestsUntilLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(3, 10_000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldRejectWhenLimitExceeded() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 10_000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-1"));

        assertFalse(limiter.tryAcquire("client-1"));
    }

    @Test
    void shouldUseSeparateCountersForDifferentClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 10_000, 10000, clock);

        assertTrue(limiter.tryAcquire("client-1"));
        assertTrue(limiter.tryAcquire("client-2"));

        assertFalse(limiter.tryAcquire("client-1"));
        assertFalse(limiter.tryAcquire("client-2"));
    }

    @Test
    void shouldResetAfterWindowExpiry() {
        SettableClock testClock = new SettableClock(1000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 500, 10000, testClock);

        assertTrue(limiter.tryAcquire("client"));
        assertTrue(limiter.tryAcquire("client"));
        assertFalse(limiter.tryAcquire("client"));

        testClock.advance(600);

        assertTrue(limiter.tryAcquire("client"));
        assertTrue(limiter.tryAcquire("client"));
        assertFalse(limiter.tryAcquire("client"));
    }

    @Test
    void shouldRejectAllRequestsWhenLimitIsZero() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(0, 10_000, 10000, clock);
        assertFalse(limiter.tryAcquire("client"));
    }

    @Test
    void shouldRejectAllRequestsWhenLimitIsNegative() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(-1, 10_000, 10000, clock);
        assertFalse(limiter.tryAcquire("client"));
    }

    @Test
    void shouldAllowAllRequestsWhenLimitIsLarge() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(Integer.MAX_VALUE, 10_000, 10000, clock);
        assertTrue(limiter.tryAcquire("client"));
    }

    // --- cache stats ---

    @Test
    void shouldReturnStatsWithHitAndMissCounts() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60_000, 10000, clock);

        limiter.tryAcquire("new-client");
        limiter.tryAcquire("new-client");
        limiter.tryAcquire("another-client");

        assertEquals(2, limiter.getMissCount());
        assertEquals(1, limiter.getHitCount());
        assertEquals(3, limiter.getRequestCount());
    }

    @Test
    void shouldReturnStatsWithZeroCountsWhenCacheIsFresh() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(5, 60_000, 10000, clock);

        assertEquals(0, limiter.getRequestCount());
        assertEquals(0, limiter.getHitCount());
        assertEquals(0, limiter.getMissCount());
    }

    @Test
    void shouldIncreaseHitCountOnRepeatedAcquire() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(10, 60_000, 10000, clock);

        limiter.tryAcquire("client");
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");

        assertEquals(2, limiter.getHitCount());
        assertEquals(1, limiter.getMissCount());
    }

    @Test
    void shouldIncreaseMissCountForDistinctClients() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(10, 60_000, 10000, clock);

        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("client-" + i);
        }

        assertEquals(10, limiter.getMissCount());
    }

    static class SettableClock extends Clock {
        private long millis;

        SettableClock(long millis) {
            this.millis = millis;
        }

        void advance(long millis) {
            this.millis += millis;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
