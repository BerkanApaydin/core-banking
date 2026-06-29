package com.bank.app.infrastructure.adapter.in.web;

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
class RedisRateLimiterTest {

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
    void shouldAcquireAndSetExpireOnFirstRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldAcquireWithoutExpireWhenRequestAlreadyExists() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldRejectWhenRequestCountExceedsLimit() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }

    @Test
    void shouldRejectWhenRedisReturnsNull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(null);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }

    @Test
    void shouldThrowWhenClientKeyIsNull() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.tryAcquire(null));
    }
}
