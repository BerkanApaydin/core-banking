package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bank.app.infrastructure.adapter.out.security.RedisLoginAttemptAdapter;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisLoginAttemptIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private RedisLoginAttemptAdapter adapter;

    @BeforeEach
    void setUp() {
        if (redisTemplate == null) {
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            redisTemplate = new StringRedisTemplate(factory);
            redisTemplate.afterPropertiesSet();
        }
        adapter = new RedisLoginAttemptAdapter(redisTemplate, 3, 15);
        adapter.reset("10.0.0.1");
        adapter.resetByUsername("testuser");
    }

    @Test
    void shouldRecordAndDetectFailure() {
        adapter.recordFailure("10.0.0.1", "testuser");
        assertThat(adapter.isIpBlocked("10.0.0.1")).isFalse();
        assertThat(adapter.isUsernameBlocked("testuser")).isFalse();
    }

    @Test
    void shouldBlockAfterMaxAttempts() {
        for (int i = 0; i < 3; i++) {
            adapter.recordFailure("10.0.0.2", "blocked_user");
        }
        assertThat(adapter.isIpBlocked("10.0.0.2")).isTrue();
        assertThat(adapter.isUsernameBlocked("blocked_user")).isTrue();
    }

    @Test
    void shouldNotBlockDifferentIp() {
        for (int i = 0; i < 3; i++) {
            adapter.recordFailure("10.0.0.3", "local_user");
        }
        assertThat(adapter.isIpBlocked("10.0.0.3")).isTrue();
        assertThat(adapter.isIpBlocked("10.0.0.4")).isFalse();
    }

    @Test
    void shouldNotBlockDifferentUsername() {
        for (int i = 0; i < 3; i++) {
            adapter.recordFailure("10.0.0.5", "user_a");
        }
        assertThat(adapter.isUsernameBlocked("user_a")).isTrue();
        assertThat(adapter.isUsernameBlocked("user_b")).isFalse();
    }

    @Test
    void shouldResetAfterResetCall() {
        adapter.recordFailure("10.0.0.6", "reset_user");
        adapter.reset("10.0.0.6");
        assertThat(adapter.isIpBlocked("10.0.0.6")).isFalse();
    }

    @Test
    void shouldResetByUsername() {
        adapter.recordFailure("10.0.0.7", "reset_by_user");
        adapter.resetByUsername("reset_by_user");
        assertThat(adapter.isUsernameBlocked("reset_by_user")).isFalse();
    }
}
