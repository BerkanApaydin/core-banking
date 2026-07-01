package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldInstantiateConditionClasses() {
        assertThat(new RedisRateLimitConfiguration()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.RateLimitRedis()).isNotNull();
        assertThat(new RedisRateLimitConfiguration.RedisBackendCondition.LoginAttemptRedis()).isNotNull();
    }
}
