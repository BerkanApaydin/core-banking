package com.bank.app.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterEdgeCaseTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                5,
                10_000);
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:client-a")), eq("10000")))
                .thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:client-b")), eq("10000")))
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
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:burst")), eq("10000")))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("burst"));
        }
        assertFalse(rateLimiter.tryAcquire("burst"));
    }

    @Test
    void shouldHandleRedisUnavailableGracefully() {
        when(redisTemplate.execute(any(), any(List.class), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> rateLimiter.tryAcquire("faulty-client"));
        assertEquals("Redis connection failed", ex.getMessage());
    }

    @Test
    void shouldHandleWhitespaceKey() {
        boolean result = rateLimiter.tryAcquire("   ");
        assertFalse(result);
    }
}
