package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientTokenBlacklistAdapterTest {

    @Mock
    private RedisTokenBlacklistAdapter redis;

    private TokenBlacklistAdapter local;
    private ResilientTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        local = new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000L, 2_592_000_000L));
        adapter = new ResilientTokenBlacklistAdapter(redis, local);
    }

    @Test
    void shouldDelegateToRedisWhenHealthy() {
        when(redis.isBlacklisted("t")).thenReturn(true);

        assertThat(adapter.isBlacklisted("t")).isTrue();
        verify(redis).isBlacklisted("t");
    }

    @Test
    void shouldFallBackToLocalBlacklistWhenRedisIsDown() {
        when(redis.isBlacklisted("t")).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(adapter.isBlacklisted("t")).isFalse();

        adapter.blacklist("t2", 60_000L);
        // Local write succeeds even though the Redis write throws.
        doThrow(new RedisConnectionFailureException("down")).when(redis).blacklist("t2", 60_000L);
        assertThatNoException().isThrownBy(() -> adapter.blacklist("t2", 60_000L));
        assertThat(local.isBlacklisted("t2")).isTrue();
    }

    @Test
    void shouldHonorLocalRevocationsDuringOutage() {
        local.blacklist("revoked", 60_000L);
        when(redis.isBlacklisted("revoked")).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(adapter.isBlacklisted("revoked")).isTrue();
    }

    @Test
    void shouldCleanBothBackends() {
        adapter.blacklist("t", 60_000L);

        adapter.cleanExpired();

        verify(redis).cleanExpired();
        assertThat(local.isBlacklisted("t")).isTrue();
    }

    @Test
    void shouldStillCleanLocalWhenRedisCleanupFails() {
        doThrow(new RedisConnectionFailureException("down")).when(redis).cleanExpired();

        assertThatNoException().isThrownBy(() -> adapter.cleanExpired());
    }
}
