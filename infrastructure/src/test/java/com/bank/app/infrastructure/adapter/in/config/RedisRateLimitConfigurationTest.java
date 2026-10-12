package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
class RedisRateLimitConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            RedisRateLimitConfiguration.class));

    @Test
    void shouldCreateRedisBeansWhenBackendIsRedis() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "app.security.rate-limit.backend=redis")
                .run(context -> {

                    assertThat(context)
                            .hasSingleBean(RedisConnectionFactory.class);

                    assertThat(context)
                            .hasSingleBean(StringRedisTemplate.class);
                });
    }

    @Test
    void shouldNotLoadConfigurationWhenBackendIsNotRedis() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "app.security.rate-limit.backend=inmemory")
                .run(context -> {

                    assertThat(context)
                            .doesNotHaveBean(RedisConnectionFactory.class);

                    assertThat(context)
                            .doesNotHaveBean(StringRedisTemplate.class);
                });
    }

    @Test
    void shouldUseHostAndPortFromProperties() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=redis-host",
                        "spring.data.redis.port=6380",
                        "app.security.rate-limit.backend=redis")
                .run(context -> {

                    RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);

                    assertThat(factory)
                            .isInstanceOf(LettuceConnectionFactory.class);

                    LettuceConnectionFactory lettuce = (LettuceConnectionFactory) factory;

                    assertThat(lettuce.getHostName())
                            .isEqualTo("redis-host");

                    assertThat(lettuce.getPort())
                            .isEqualTo(6380);
                });
    }

    @Test
    void shouldBindAclTlsAndCommandTimeout() {
        contextRunner.withPropertyValues(
                "app.security.rate-limit.backend=redis",
                "spring.data.redis.username=service-user",
                "spring.data.redis.password=secret",
                "spring.data.redis.ssl.enabled=true",
                "spring.data.redis.timeout=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getStandaloneConfiguration().getUsername()).isEqualTo("service-user");
                    assertThat(factory.getPassword()).isEqualTo("secret");
                    assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
                    assertThat(factory.getClientConfiguration().getCommandTimeout())
                            .isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void shouldCreateStringRedisTemplateWithConnectionFactory() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "app.security.rate-limit.backend=redis")
                .run(context -> {
                    StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
                    assertThat(template.getConnectionFactory()).isNotNull();
                    assertThat(template.getConnectionFactory())
                            .isSameAs(context.getBean(RedisConnectionFactory.class));
                });
    }

    @Test
    void shouldUseDefaultHostAndPortWhenNotSet() {
        contextRunner
                .withPropertyValues(
                        "app.security.rate-limit.backend=redis")
                .run(context -> {

                    RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);

                    assertThat(factory)
                            .isInstanceOf(LettuceConnectionFactory.class);

                    LettuceConnectionFactory lettuce = (LettuceConnectionFactory) factory;

                    assertThat(lettuce.getHostName())
                            .isEqualTo("localhost");

                    assertThat(lettuce.getPort())
                            .isEqualTo(6379);
                });
    }

    @Test
    void shouldCreateRedisBeansWhenSnapshotCacheBackendIsRedis() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "app.cache.caffeine.account-info.backend=redis")
                .run(context -> {

                    assertThat(context)
                            .hasSingleBean(RedisConnectionFactory.class);

                    assertThat(context)
                            .hasSingleBean(StringRedisTemplate.class);
                });
    }

    @Test
    void shouldApplyDatabaseIndex() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);
        properties.setDatabase(3);

        LettuceConnectionFactory factory =
                (LettuceConnectionFactory) new RedisRateLimitConfiguration().redisConnectionFactory(properties);

        assertThat(factory.getStandaloneConfiguration().getDatabase()).isEqualTo(3);
    }

    @Test
    void shouldRejectUrlSentinelAndClusterConfigurations() {
        RedisRateLimitConfiguration configuration = new RedisRateLimitConfiguration();

        RedisProperties withUrl = new RedisProperties();
        withUrl.setUrl("redis://localhost:6379");
        assertThatThrownBy(() -> configuration.redisConnectionFactory(withUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone");

        RedisProperties withSentinel = new RedisProperties();
        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster("mymaster");
        withSentinel.setSentinel(sentinel);
        assertThatThrownBy(() -> configuration.redisConnectionFactory(withSentinel))
                .isInstanceOf(IllegalArgumentException.class);

        RedisProperties withCluster = new RedisProperties();
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();
        cluster.setNodes(java.util.List.of("localhost:6379"));
        withCluster.setCluster(cluster);
        assertThatThrownBy(() -> configuration.redisConnectionFactory(withCluster))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSslBundle() {
        RedisProperties properties = new RedisProperties();
        properties.getSsl().setBundle("my-bundle");

        assertThatThrownBy(() -> new RedisRateLimitConfiguration().redisConnectionFactory(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSL bundles");
    }

    @Test
    void shouldApplyPoolBounds() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);
        properties.getLettuce().getPool().setMaxActive(5);
        properties.getLettuce().getPool().setMaxIdle(4);
        properties.getLettuce().getPool().setMinIdle(2);
        properties.getLettuce().getPool().setMaxWait(Duration.ofSeconds(3));

        LettuceConnectionFactory factory =
                (LettuceConnectionFactory) new RedisRateLimitConfiguration().redisConnectionFactory(properties);

        assertThat(factory.getClientConfiguration())
                .isInstanceOf(LettucePoolingClientConfiguration.class);
        LettucePoolingClientConfiguration pooling =
                (LettucePoolingClientConfiguration) factory.getClientConfiguration();
        assertThat(pooling.getPoolConfig().getMaxTotal()).isEqualTo(5);
        assertThat(pooling.getPoolConfig().getMaxIdle()).isEqualTo(4);
        assertThat(pooling.getPoolConfig().getMinIdle()).isEqualTo(2);
        assertThat(pooling.getPoolConfig().getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldSkipNonPositivePoolBounds() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);
        properties.getLettuce().getPool().setMaxActive(0);
        properties.getLettuce().getPool().setMaxIdle(0);
        properties.getLettuce().getPool().setMinIdle(0);
        properties.getLettuce().getPool().setMaxWait(null);

        LettuceConnectionFactory factory =
                (LettuceConnectionFactory) new RedisRateLimitConfiguration().redisConnectionFactory(properties);

        assertThat(factory.getClientConfiguration())
                .isInstanceOf(LettucePoolingClientConfiguration.class);
        LettucePoolingClientConfiguration pooling =
                (LettucePoolingClientConfiguration) factory.getClientConfiguration();
        // Commons-pool2 defaults survive when bounds are non-positive/absent.
        assertThat(pooling.getPoolConfig().getMaxTotal()).isEqualTo(8);
        assertThat(pooling.getPoolConfig().getMaxIdle()).isEqualTo(8);
        assertThat(pooling.getPoolConfig().getMinIdle()).isEqualTo(0);
    }

    @Test
    void shouldConfigureRedisTemplateSerializers() {
        RedisProperties properties = new RedisProperties();
        LettuceConnectionFactory factory =
                (LettuceConnectionFactory) new RedisRateLimitConfiguration().redisConnectionFactory(properties);

        RedisTemplate<Object, Object> template =
                new RedisRateLimitConfiguration().redisTemplate(factory);

        assertThat(template).isNotNull();
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getConnectionFactory()).isSameAs(factory);
    }

    @Test
    void shouldInstantiateConditionClasses() {
        assertThat(new RedisRateLimitConfiguration()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.RateLimitRedis()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.LoginAttemptRedis()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.TokenBlacklistRedis()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.SnapshotCacheRedis()).isNotNull();
    }
}
