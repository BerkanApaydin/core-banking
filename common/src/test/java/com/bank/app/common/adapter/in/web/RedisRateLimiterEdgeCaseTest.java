package com.bank.app.common.adapter.in.web;

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

@SuppressWarnings({"null", "unchecked"})
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterEdgeCaseTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setMaxRequests(5);
        props.setTimeWindowMs(10_000);
        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                props);
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
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
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 0L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("burst"));
        }
        assertFalse(rateLimiter.tryAcquire("burst"));
    }

    @Test
    void shouldHandleRedisUnavailableGracefully() {
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString()))
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
