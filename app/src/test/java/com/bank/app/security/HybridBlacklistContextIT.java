package com.bank.app.security;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.bank.app.infrastructure.adapter.out.security.DatabaseTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.HybridTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.RedisTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.security.token-blacklist.backend=hybrid")
class HybridBlacklistContextIT extends AbstractSpringBootIntegrationTest {

    @Container
    private static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    private final TokenBlacklistPort blacklist;
    private final JwtPort jwtPort;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    HybridBlacklistContextIT(TokenBlacklistPort blacklist, JwtPort jwtPort, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, StringRedisTemplate redisTemplate,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.blacklist = blacklist;
        this.jwtPort = jwtPort;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.redisTemplate = redisTemplate;
    }

    @Test
    void shouldKeepNewRevocationAcrossPodsAndRedisDataLoss() {
        assertThat(blacklist).isExactlyInstanceOf(HybridTokenBlacklistAdapter.class);
        String token = jwtPort.generateToken(101L, "hybrid-test-user");
        blacklist.blacklist(token, jwtPort.getRemainingMs(token));

        Set<String> keys = redisTemplate.keys("token_blacklist:sha256:*");
        assertThat(keys).isNotEmpty();
        redisTemplate.delete(keys);

        // A fresh adapter has no local state and the Redis key is gone.
        var otherPod = new HybridTokenBlacklistAdapter(
                new DatabaseTokenBlacklistAdapter(jdbc, transactionManager, jwtPort),
                new RedisTokenBlacklistAdapter(redisTemplate),
                new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000, 2_592_000_000L)));
        assertThat(otherPod.isBlacklisted(token)).isTrue();
    }

    @Test
    void shouldHonorPreMigrationRedisOnlyRevocation() {
        String token = jwtPort.generateToken(102L, "legacy-test-user");
        redisTemplate.opsForValue().set("token_blacklist:" + token,
                "blacklisted", 60_000, TimeUnit.MILLISECONDS);

        var otherPod = new HybridTokenBlacklistAdapter(
                new DatabaseTokenBlacklistAdapter(jdbc, transactionManager, jwtPort),
                new RedisTokenBlacklistAdapter(redisTemplate),
                new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000, 2_592_000_000L)));
        assertThat(otherPod.isBlacklisted(token)).isTrue();
    }
}
