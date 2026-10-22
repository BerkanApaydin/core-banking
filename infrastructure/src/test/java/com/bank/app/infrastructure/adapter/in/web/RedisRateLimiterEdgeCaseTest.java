package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"null", "unchecked"})
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterEdgeCaseTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(null, null, 5, 10_000, 120, 60000, 10000);
        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                props);
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        assertTrue(rateLimiter.tryAcquire("client-a"));
        assertTrue(rateLimiter.tryAcquire("client-b"));
    }

    @Test
    void shouldHandleEmptyClientKey() {
        boolean result = rateLimiter.tryAcquire("");
        assertFalse(result);
    }

    @Test
    void shouldHandleHighVolumeForSingleClient() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 0L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("burst"));
        }
        assertFalse(rateLimiter.tryAcquire("burst"));
    }

    @Test
    void shouldHandleRedisUnavailableGracefully() {
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> rateLimiter.tryAcquire("faulty-client"));
        assertEquals("Redis connection failed", ex.getMessage());
    }

    @Test
    void shouldPropagateDataAccessFailuresToTheCallerInsteadOfDecidingLocally() {
        // The limiter does not translate backend failures: it must not answer
        // "allowed" (fail-open) nor invent a rate-limit decision. The caller
        // (AbstractRateLimitingFilter) owns the fail-closed 503.
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThrows(DataAccessException.class,
                () -> rateLimiter.tryAcquire("faulty-client"));
    }

    @Test
    void shouldPropagateNonDataAccessFailuresUntranslated() {
        // A client-side failure that is not a DataAccessException carries no
        // rate-limit semantics at all; it must surface as-is instead of being
        // silently downgraded to an allow/deny decision.
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis client not initialized"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> rateLimiter.tryAcquire("faulty-client"));
        assertEquals("redis client not initialized", ex.getMessage());
    }

    @Test
    void shouldHandleWhitespaceKey() {
        boolean result = rateLimiter.tryAcquire("   ");
        assertFalse(result);
    }
}
