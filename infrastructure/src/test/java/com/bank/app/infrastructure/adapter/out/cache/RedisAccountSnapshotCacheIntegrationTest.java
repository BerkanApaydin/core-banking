package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.infrastructure.adapter.in.config.CacheProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisAccountSnapshotCacheIntegrationTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void setUp() {
        REDIS.start();
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void evictAllScansOnlySnapshotNamespace() {
        template.opsForValue().set("account-snapshot:id-1", "value");
        template.opsForValue().set("account-snapshot:batch:one", "value");
        template.opsForValue().set("unrelated:test-key", "keep");

        new RedisAccountSnapshotCacheAdapter(template, new CacheProperties()).evictAll();

        assertFalse(Boolean.TRUE.equals(template.hasKey("account-snapshot:id-1")));
        assertFalse(Boolean.TRUE.equals(template.hasKey("account-snapshot:batch:one")));
        assertTrue(Boolean.TRUE.equals(template.hasKey("unrelated:test-key")));
    }
}
