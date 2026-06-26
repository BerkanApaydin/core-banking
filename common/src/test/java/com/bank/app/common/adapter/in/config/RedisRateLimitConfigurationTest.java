package com.bank.app.common.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
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
                .withBean(RedisProperties.class, () -> {
                    RedisProperties props = new RedisProperties();
                    props.setHost("localhost");
                    props.setPort(6379);
                    return props;
                })
                .withPropertyValues(
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
                .withBean(RedisProperties.class, RedisProperties::new)
                .withPropertyValues(
                        "app.security.rate-limit.backend=inmemory")
                .run(context -> {

                    assertThat(context)
                            .doesNotHaveBean(RedisConnectionFactory.class);

                    assertThat(context)
                            .doesNotHaveBean(StringRedisTemplate.class);
                });
    }

    @Test
    void shouldUseHostAndPortFromRedisProperties() {
        contextRunner
                .withBean(RedisProperties.class, () -> {
                    RedisProperties props = new RedisProperties();
                    props.setHost("redis-host");
                    props.setPort(6380);
                    return props;
                })
                .withPropertyValues(
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
                .withBean(RedisProperties.class, () -> {
                    RedisProperties props = new RedisProperties();
                    props.setHost("localhost");
                    props.setPort(6379);
                    return props;
                })
                .withPropertyValues(
                        "app.security.rate-limit.backend=redis")
                .run(context -> {
                    StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
                    assertThat(template.getConnectionFactory()).isNotNull();
                    assertThat(template.getConnectionFactory())
                            .isSameAs(context.getBean(RedisConnectionFactory.class));
                });
    }

    @Test
    void shouldThrowWhenRedisHostIsNull() {
        contextRunner
                .withBean(RedisProperties.class, () -> new RedisProperties() {
                    @Override
                    public String getHost() {
                        return null;
                    }
                })
                .withPropertyValues(
                        "app.security.rate-limit.backend=redis")
                .run(context -> {
                    assertThat(context).hasFailed();
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
