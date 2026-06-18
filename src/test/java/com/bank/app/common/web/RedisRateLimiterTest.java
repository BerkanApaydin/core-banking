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
class RedisRateLimiterTest {

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
    void shouldAcquireAndSetExpireOnFirstRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:user1")), eq("10000")))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldAcquireWithoutExpireWhenRequestAlreadyExists() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:user1")), eq("10000")))
                .thenReturn(3L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldRejectWhenRequestCountExceedsLimit() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:user1")), eq("10000")))
                .thenReturn(6L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }

    @Test
    void shouldRejectWhenRedisReturnsNull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:user1")), eq("10000")))
                .thenReturn(null);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }
}
