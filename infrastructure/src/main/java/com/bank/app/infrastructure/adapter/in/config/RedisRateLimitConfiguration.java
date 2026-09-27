package com.bank.app.infrastructure.adapter.in.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;

@Configuration
@Conditional(RedisRateLimitConfiguration.RedisBackendCondition.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisRateLimitConfiguration {

    @Bean
    RedisConnectionFactory redisConnectionFactory(RedisProperties properties) {
        if (properties.getUrl() != null || properties.getSentinel() != null || properties.getCluster() != null) {
            throw new IllegalArgumentException("This Redis backend supports standalone host/port configuration only");
        }
        if (properties.getSsl().getBundle() != null) {
            throw new IllegalArgumentException("Redis SSL bundles require Boot Redis auto-configuration");
        }
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        server.setDatabase(properties.getDatabase());
        if (properties.getUsername() != null) server.setUsername(properties.getUsername());
        if (properties.getPassword() != null) server.setPassword(RedisPassword.of(properties.getPassword()));

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder();
        if (properties.getSsl().isEnabled()) client.useSsl();
        if (properties.getTimeout() != null) client.commandTimeout(properties.getTimeout());
        if (properties.getConnectTimeout() != null) {
            client.clientOptions(ClientOptions.builder().socketOptions(
                    SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build()).build());
        }
        if (properties.getClientName() != null) client.clientName(properties.getClientName());
        return new LettuceConnectionFactory(server, client.build());
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(@NonNull RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    RedisTemplate<Object, Object> redisTemplate(@NonNull RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    static class RedisBackendCondition extends AnyNestedCondition {

        RedisBackendCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
        static class RateLimitRedis {}

        @ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis")
        static class LoginAttemptRedis {}

        @ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "redis")
        static class TokenBlacklistRedis {}

        @ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "hybrid")
        static class TokenBlacklistHybrid {}

        @ConditionalOnProperty(name = "app.cache.caffeine.account-info.backend", havingValue = "redis")
        static class SnapshotCacheRedis {}
    }
}
