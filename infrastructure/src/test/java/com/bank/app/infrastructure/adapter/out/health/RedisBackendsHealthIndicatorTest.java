package com.bank.app.infrastructure.adapter.out.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisBackendsHealthIndicatorTest {

    @Test
    void shouldReportUpWhenRedisIsNotConfigured() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisConnectionFactory> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        Health health = new RedisBackendsHealthIndicator(empty).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldReportUpOnPong() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("PONG");
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);

        Health health = new RedisBackendsHealthIndicator(provider).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldReportDownWhenRedisThrows() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("connection refused"));
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);

        Health health = new RedisBackendsHealthIndicator(provider).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
