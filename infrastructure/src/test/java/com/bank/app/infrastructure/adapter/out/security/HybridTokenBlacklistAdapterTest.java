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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridTokenBlacklistAdapterTest {

    @Mock private DatabaseTokenBlacklistAdapter database;
    @Mock private RedisTokenBlacklistAdapter redis;

    private TokenBlacklistAdapter local;
    private HybridTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        local = new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000, 2_592_000_000L));
        adapter = new HybridTokenBlacklistAdapter(database, redis, local);
    }

    @Test
    void shouldHonorLegacyRedisOnlyRevocation() {
        when(redis.isBlacklisted("token")).thenReturn(true);

        assertThat(adapter.isBlacklisted("token")).isTrue();
        // Redis is consulted BEFORE the durable store: every revocation
        // written while the hybrid backend is active is in both, and the
        // shared store is the cheaper lookup.
        verify(redis).isBlacklisted("token");
        verify(database, never()).isBlacklisted("token");
    }

    @Test
    void shouldHonorDurableRevocationWhenRedisLostItsData() {
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token")).thenReturn(true);

        assertThat(adapter.isBlacklisted("token")).isTrue();
        verify(database).isBlacklisted("token");
    }

    @Test
    void shouldNotReportSuccessfulLogoutIfLegacyPodsCannotSeeWrite() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(redis).blacklist("token", 60_000);

        assertThatThrownBy(() -> adapter.blacklist("token", 60_000))
                .isInstanceOf(RevocationStoreUnavailableException.class);
        verify(database).blacklist("token", 60_000);
        assertThat(local.isBlacklisted("token")).isFalse();
    }

    @Test
    void shouldFailClosedWhenDurableLookupFails() {
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token"))
                .thenThrow(new RevocationStoreUnavailableException(new RuntimeException("down")));

        assertThatThrownBy(() -> adapter.isBlacklisted("token"))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void shouldFailClosedWhenRedisReadFailsAfterDurableMiss() {
        when(redis.isBlacklisted("token"))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> adapter.isBlacklisted("token"))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void shouldFanOutCleanupToAllThreeBackends() {
        adapter.cleanExpired();

        verify(database).cleanExpired();
        verify(redis).cleanExpired();
        assertThat(local.isBlacklisted("anything")).isFalse();
    }
}
