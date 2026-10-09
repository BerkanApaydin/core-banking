package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.infrastructure.adapter.in.config.CacheProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;
import java.util.Set;

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

        new RedisAccountSnapshotCacheAdapter(template, new CacheProperties.AccountInfoCache("caffeine", 1000, 60, 500)).evictAll();

        assertFalse(Boolean.TRUE.equals(template.hasKey("account-snapshot:id-1")));
        assertFalse(Boolean.TRUE.equals(template.hasKey("account-snapshot:batch:one")));
        assertTrue(Boolean.TRUE.equals(template.hasKey("unrelated:test-key")));
    }

    @Test
    void evictByIdDropsDistributedIndexEntriesWrittenByAnotherPod() {
        var adapter = new RedisAccountSnapshotCacheAdapter(template, new CacheProperties.AccountInfoCache("caffeine", 1000, 60, 500));
        var snapshot = new AccountSnapshot(7L, 70L, "TRY", "ACTIVE");
        String iban = "TR330006100519786457841326";
        // Another pod's writes: IBAN snapshot + reverse index + id mapping.
        adapter.putByIban(iban, snapshot);
        adapter.putIbans(Set.of(7L), Map.of(7L, iban));
        assertTrue(adapter.getByIban(iban).isPresent());

        adapter.evictById(7L);

        assertTrue(adapter.getById(7L).isEmpty());
        assertTrue(adapter.getByIban(iban).isEmpty());
        assertTrue(adapter.getIbans(Set.of(7L)).isEmpty());
    }

    @Test
    void putIbansWritesTtlEntriesReadableBack() {
        var adapter = new RedisAccountSnapshotCacheAdapter(template, new CacheProperties.AccountInfoCache("caffeine", 1000, 60, 500));

        adapter.putIbans(Set.of(8L, 9L), Map.of(8L, "TR8", 9L, "TR9"));

        assertTrue(adapter.getIbans(Set.of(8L, 9L)).isPresent());
        Long ttl = template.getExpire("account-snapshot:iban-of:8");
        assertTrue(ttl != null && ttl > 0 && ttl <= 60);
    }
}
