package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;

@Configuration
@Conditional(RedisRateLimitConfiguration.RedisBackendCondition.class)
public class RedisRateLimitConfiguration {

    @Bean
    RedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Redis host must not be null");
        }
        return new LettuceConnectionFactory(host, redisProperties.getPort());
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(@NonNull RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    static class RedisBackendCondition extends AnyNestedCondition {

        RedisBackendCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
        static class RateLimitRedis {}

        @ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis")
        static class LoginAttemptRedis {}
    }
}
