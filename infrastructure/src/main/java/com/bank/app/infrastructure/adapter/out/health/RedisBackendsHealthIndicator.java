package com.bank.app.infrastructure.adapter.out.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis liveness for the token-blacklist / rate-limiter / login-attempt backends.
 *
 * <p>{@code RedisAutoConfiguration} is excluded in {@code application.yml}, so no
 * framework-provided Redis indicator exists. When no Redis factory is configured
 * (all-caffeine mode) this reports UP as "not applicable" instead of failing —
 * a missing optional dependency must not flip readiness.
 */
@Component("redisBackends")
public class RedisBackendsHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RedisConnectionFactory> connectionFactory;

    public RedisBackendsHealthIndicator(ObjectProvider<RedisConnectionFactory> connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        RedisConnectionFactory factory = connectionFactory.getIfAvailable();
        if (factory == null) {
            return Health.up().withDetail("redis", "not configured").build();
        }
        try {
            String pong = factory.getConnection().ping();
            return Health.up().withDetail("redis", pong).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
