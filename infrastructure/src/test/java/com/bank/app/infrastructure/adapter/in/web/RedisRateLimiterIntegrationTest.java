package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterIntegrationTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\s", 1));

    private static RedisRateLimiter rateLimiter;

    @BeforeAll
    static void setup() {
        redis.start();
        var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        var redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
        var props = new RateLimitProperties();
        props.setMaxRequests(5);
        props.setTimeWindowMs(10_000);
        rateLimiter = new RedisRateLimiter(redisTemplate, props);
    }

    @AfterAll
    static void teardown() {
        redis.stop();
    }

    @Test
    void shouldAllowRequestUnderLimit() {
        assertThat(rateLimiter.tryAcquire("client-1")).isTrue();
    }

    @Test
    void shouldBlockRequestOverLimit() {
        String client = "burst-client";
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(client);
        }
        assertThat(rateLimiter.tryAcquire(client)).isFalse();
    }

    @Test
    void shouldAllowDifferentClientsIndependently() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("heavy-client");
        }
        assertThat(rateLimiter.tryAcquire("heavy-client")).isFalse();
        assertThat(rateLimiter.tryAcquire("light-client")).isTrue();
    }

    @Test
    void shouldHandleWhitespaceKey() {
        assertThat(rateLimiter.tryAcquire("   ")).isTrue();
    }

    @Test
    void shouldHandleNullKey() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> rateLimiter.tryAcquire(null));
    }
}
