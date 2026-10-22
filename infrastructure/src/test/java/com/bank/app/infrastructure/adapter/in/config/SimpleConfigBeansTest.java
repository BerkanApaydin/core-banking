package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.common.application.port.out.SecurityContextPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.out.clock.UuidIdGeneratorAdapter;
import com.bank.app.infrastructure.adapter.out.security.DatabaseTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.HybridTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class SimpleConfigBeansTest {

    @ApiVersion
    static class VersionedController {
    }

    static class PlainController {
    }

    @Test
    void commonBeansAreConstructed() {
        CommonBeanConfig config = new CommonBeanConfig();
        SecurityContextPort securityContextPort = mock(SecurityContextPort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);

        assertThat(config.userContextService(securityContextPort)).isInstanceOf(UserContextService.class);
        ClockProviderPort clock = config.clockProvider();
        assertThat(clock).isNotNull();
        assertThat(clock.clock()).isNotNull();
        IdGeneratorPort ids = config.idGeneratorPort();
        org.assertj.core.api.Assertions.assertThatCode(() -> UUID.fromString(ids.newId()))
                .doesNotThrowAnyException();
        assertThat(config.domainEventPublisherService(eventPublisherPort))
                .isInstanceOf(DomainEventPublisherService.class);
    }

    @Test
    void tokenBlacklistBackendBeansAreConstructed() {
        TokenBlacklistBackendConfig config = new TokenBlacklistBackendConfig();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        JwtPort jwtPort = mock(JwtPort.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TokenBlacklistAdapter local = new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000L, 10_000L));
        TokenBlacklistProperties properties = new TokenBlacklistProperties(1_000L, 10_000L);

        TokenBlacklistPort database = config.databaseTokenBlacklist(jdbc, transactionManager, jwtPort);
        TokenBlacklistPort hybrid = config.hybridTokenBlacklist(
                jdbc, transactionManager, jwtPort, redis, local, properties);

        assertThat(database).isInstanceOf(DatabaseTokenBlacklistAdapter.class);
        assertThat(hybrid).isInstanceOf(HybridTokenBlacklistAdapter.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void apiVersionConfigPrefixesOnlyAnnotatedControllers() {
        PathMatchConfigurer configurer = mock(PathMatchConfigurer.class);
        Predicate<Class<?>> predicate = mock(Predicate.class);

        new ApiVersionConfig().configurePathMatch(configurer);

        var captor = org.mockito.ArgumentCaptor.forClass(Predicate.class);
        verify(configurer).addPathPrefix(eq("/api/v1"), captor.capture());
        Predicate<Class<?>> captured = captor.getValue();
        assertThat(captured.test(VersionedController.class)).isTrue();
        assertThat(captured.test(PlainController.class)).isFalse();
        assertThat(predicate).isNotNull();
    }

    @Test
    void uuidIdGeneratorProducesUniqueUuids() {
        UuidIdGeneratorAdapter adapter = new UuidIdGeneratorAdapter();
        String first = adapter.newId();
        String second = adapter.newId();

        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void cacheManagerUsesConfiguredCaffeinePolicy() {
        CacheProperties.AccountInfoCache settings =
                new CacheProperties.AccountInfoCache("caffeine", 4321, 17, 500);
        CaffeineCache cache = (CaffeineCache) new CacheConfig(settings)
                .cacheManager().getCache("accountAclInfo");

        assertThat(cache).isNotNull();
        assertThat(cache.getNativeCache().policy().eviction().orElseThrow().getMaximum())
                .isEqualTo(4321L);
        assertThat(cache.getNativeCache().policy().expireAfterWrite().orElseThrow()
                .getExpiresAfter(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(17L);
    }

    @Test
    void snapshotCacheConditionsReportBackendSpecificOutcomes() {
        ConditionContext context = mock(ConditionContext.class);
        MockEnvironment caffeine = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "caffeine");
        MockEnvironment redis = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis");

        when(context.getEnvironment()).thenReturn(caffeine);
        ConditionOutcome caffeineMatch = new SnapshotCacheCaffeineCondition()
                .getMatchOutcome(context, null);
        ConditionOutcome redisMismatch = new SnapshotCacheRedisCondition()
                .getMatchOutcome(context, null);
        assertThat(caffeineMatch.isMatch()).isTrue();
        assertThat(caffeineMatch.getMessage().toString()).contains("is single-JVM");
        assertThat(redisMismatch.isMatch()).isFalse();
        assertThat(redisMismatch.getMessage().toString()).contains("is not redis");

        when(context.getEnvironment()).thenReturn(redis);
        ConditionOutcome caffeineMismatch = new SnapshotCacheCaffeineCondition()
                .getMatchOutcome(context, null);
        ConditionOutcome redisMatch = new SnapshotCacheRedisCondition()
                .getMatchOutcome(context, null);
        assertThat(caffeineMismatch.isMatch()).isFalse();
        assertThat(caffeineMismatch.getMessage().toString()).contains("is not single-JVM");
        assertThat(redisMatch.isMatch()).isTrue();
        assertThat(redisMatch.getMessage().toString()).contains("is redis");
    }

    @Test
    void redisPoolingMinIdleIsAppliedUnconditionally() {
        org.springframework.boot.autoconfigure.data.redis.RedisProperties properties =
                new org.springframework.boot.autoconfigure.data.redis.RedisProperties();
        properties.getLettuce().getPool().setMinIdle(0);

        var factory = (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                new RedisRateLimitConfiguration().redisConnectionFactory(properties);
        var pooling = (org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration)
                factory.getClientConfiguration();

        assertThat(pooling.getPoolConfig().getMinIdle()).isZero();
    }

}
