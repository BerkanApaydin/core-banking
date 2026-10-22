package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        RateLimitProperties props = new RateLimitProperties(null, null, 5, 10_000, 120, 60000, 10000);
        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                props);
    }

    @Test
    void shouldAcquireAndSetExpireOnFirstRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldAcquireWithoutExpireWhenRequestAlreadyExists() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertTrue(result);
    }

    @Test
    void shouldRejectWhenRequestCountExceedsLimit() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }

    @Test
    void shouldRejectWhenRedisReturnsNull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        boolean result = rateLimiter.tryAcquire("user1");

        assertFalse(result);
    }

    @Test
    void shouldThrowWhenClientKeyIsNull() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.tryAcquire(null));
    }

    @Test
    void shouldPassTierBudgetToLuaScript() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        assertTrue(rateLimiter.tryAcquire("user1", 120, 60_000));

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("rate_limit:sliding:user1")),
                anyString(), eq("60000"), eq("120"),
                argThat(value -> value instanceof String s && !s.isBlank()));
    }

    @Test
    void shouldUseSystemClockWhenProviderIsNull() {
        RateLimitProperties props = new RateLimitProperties(null, null, 5, 10_000, 120, 60000, 10000);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(),
                anyString(), anyString(), anyString())).thenReturn(1L);

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, props,
                (com.bank.app.common.application.port.out.ClockProviderPort) null);

        assertTrue(limiter.tryAcquire("user1"));
    }

    @Test
    void shouldUseClockProviderClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
        com.bank.app.common.application.port.out.ClockProviderPort provider =
                org.mockito.Mockito.mock(com.bank.app.common.application.port.out.ClockProviderPort.class);
        when(provider.clock()).thenReturn(fixed);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(),
                anyString(), anyString(), anyString())).thenReturn(1L);

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate,
                new RateLimitProperties(null, null, 5, 10_000, 120, 60000, 10000), provider);

        assertTrue(limiter.tryAcquire("user1"));
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                any(List.class), eq(String.valueOf(fixed.millis())), anyString(), anyString(), anyString());
    }

    @Test
    void shouldUseInjectedClockForLuaTimestamp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
        RateLimitProperties props = new RateLimitProperties(null, null, 5, 10_000, 120, 60000, 10000);
        RedisRateLimiter fixedLimiter = new RedisRateLimiter(redisTemplate, props, fixed);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        assertTrue(fixedLimiter.tryAcquire("user1"));

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("rate_limit:sliding:user1")),
                eq(String.valueOf(fixed.millis())), anyString(), anyString(), anyString());
    }
}
