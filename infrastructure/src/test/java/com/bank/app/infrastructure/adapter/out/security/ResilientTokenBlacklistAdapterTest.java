package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
    void shouldRefuseUnknownTokenWhenRedisIsDown() {
        when(redis.isBlacklisted("t")).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> adapter.isBlacklisted("t"))
                .isInstanceOf(RevocationStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void shouldHonorLocalRevocationsDuringOutage() {
        local.blacklist("revoked", 60_000L);

        assertThat(adapter.isBlacklisted("revoked")).isTrue();
        verify(redis, never()).isBlacklisted("revoked");
    }

    @Test
    void shouldReportFailedSharedWriteAndAllowLogoutRetry() {
        doThrow(new RedisConnectionFailureException("down")).doNothing()
                .when(redis).blacklist("revoked", 60_000L);
        assertThatThrownBy(() -> adapter.blacklist("revoked", 60_000L))
                .isInstanceOf(RevocationStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);

        assertThat(local.isBlacklisted("revoked")).isFalse();
        adapter.blacklist("revoked", 60_000L);
        assertThat(local.isBlacklisted("revoked")).isTrue();
        verify(redis, times(2)).blacklist("revoked", 60_000L);
    }

    @Test
    void shouldKeepConfirmedRevocationAfterRedisLosesEntry() {
        adapter.blacklist("revoked", 60_000L);

        assertThat(adapter.isBlacklisted("revoked")).isTrue();
        verify(redis, never()).isBlacklisted("revoked");
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
