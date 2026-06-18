package com.bank.app.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                5,
                10_000);
    }

    @Test
    void shouldAcquireAndSetExpireOnFirstRequest() {
        when(valueOperations.increment("rate_limit:user1"))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);

        verify(redisTemplate)
                .expire(
                        "rate_limit:user1",
                        Duration.ofMillis(10_000));
    }

    @Test
    void shouldAcquireWithoutExpireWhenRequestAlreadyExists() {
        when(valueOperations.increment("rate_limit:user1"))
                .thenReturn(3L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);

        verify(redisTemplate, never())
                .expire(anyString(), any());
    }

    @Test
    void shouldRejectWhenRequestCountExceedsLimit() {
        when(valueOperations.increment("rate_limit:user1"))
                .thenReturn(6L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);

        verify(redisTemplate, never())
                .expire(anyString(), any());
    }

    @Test
    void shouldRejectWhenRedisReturnsNull() {
        when(valueOperations.increment("rate_limit:user1"))
                .thenReturn(null);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);

        verify(redisTemplate, never())
                .expire(anyString(), any());
    }
}