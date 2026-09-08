package com.bank.app.security;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.infrastructure.adapter.out.security.ResilientTokenBlacklistAdapter;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the redis-mode blacklist wiring that no unit test can cover:
 * with {@code app.security.token-blacklist.backend=redis} the context must
 * resolve the resilient decorator (not the raw Redis/Caffeine beans) and a
 * logout round-trip must work against real Redis.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.security.token-blacklist.backend=redis")
class ResilientBlacklistContextIT extends AbstractSpringBootIntegrationTest {

    @Container
    private static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private TokenBlacklistPort blacklistPort;

    @Test
    void shouldResolveResilientDecoratorInRedisMode() {
        assertThat(blacklistPort).isExactlyInstanceOf(ResilientTokenBlacklistAdapter.class);
    }

    @Test
    void shouldRoundTripRevocationThroughRedis() {
        blacklistPort.blacklist("ctx-token", 60_000L);

        assertThat(blacklistPort.isBlacklisted("ctx-token")).isTrue();
        assertThat(blacklistPort.isBlacklisted("ctx-unknown")).isFalse();
    }
}
