package com.bank.app.infrastructure.adapter.out.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTokenBlacklistIntegrationTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static RedisTokenBlacklistAdapter adapter;

    @BeforeAll
    static void setup() {
        redis.start();
        var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        var redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
        adapter = new RedisTokenBlacklistAdapter(redisTemplate);
    }

    @AfterAll
    static void teardown() {
        redis.stop();
    }

    @Test
    void shouldBlacklistToken() {
        adapter.blacklist("test-token", 60000);
        assertThat(adapter.isBlacklisted("test-token")).isTrue();
    }

    @Test
    void shouldNotFindNonBlacklistedToken() {
        assertThat(adapter.isBlacklisted("non-existent-token")).isFalse();
    }

    @Test
    void shouldExpireTokenAfterTtl() throws InterruptedException {
        adapter.blacklist("expiring-token", 100);
        assertThat(adapter.isBlacklisted("expiring-token")).isTrue();
        Thread.sleep(200);
        assertThat(adapter.isBlacklisted("expiring-token")).isFalse();
    }

    @Test
    void shouldHandleMultipleTokens() {
        adapter.blacklist("token-a", 60000);
        adapter.blacklist("token-b", 60000);
        assertThat(adapter.isBlacklisted("token-a")).isTrue();
        assertThat(adapter.isBlacklisted("token-b")).isTrue();
        assertThat(adapter.isBlacklisted("token-c")).isFalse();
    }

    @Test
    void cleanExpiredShouldBeNoOp() {
        adapter.blacklist("clean-test", 60000);
        adapter.cleanExpired();
        assertThat(adapter.isBlacklisted("clean-test")).isTrue();
    }
}
