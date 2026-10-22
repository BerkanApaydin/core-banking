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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        // R-I12: the local overlay is written before the shared stores, so this
        // pod is fail-closed even when Redis cannot be updated.
        assertThat(local.isBlacklisted("token")).isTrue();
    }

    @Test
    void shouldStillAttemptRedisWhenDatabaseWriteFailsAndKeepLocalRevocation() {
        doThrow(new RevocationStoreUnavailableException(new RuntimeException("db down")))
                .when(database).blacklist("token", 60_000);

        assertThatThrownBy(() -> adapter.blacklist("token", 60_000))
                .isInstanceOf(RevocationStoreUnavailableException.class);
        verify(redis).blacklist("token", 60_000);
        assertThat(local.isBlacklisted("token")).isTrue();
    }

    @Test
    void shouldInvalidateNegativeCacheOnSuccessfulBlacklist() {
        TokenBlacklistAdapter localMock = mock(TokenBlacklistAdapter.class);
        HybridTokenBlacklistAdapter hybrid = new HybridTokenBlacklistAdapter(database, redis, localMock);
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token")).thenReturn(false, true);

        // Populate the negative cache with a durable "not revoked" answer.
        assertThat(hybrid.isBlacklisted("token")).isFalse();

        hybrid.blacklist("token", 60_000);

        // Without invalidation the stale negative entry would answer false
        // without touching the database again.
        assertThat(hybrid.isBlacklisted("token")).isTrue();
        verify(database, times(2)).isBlacklisted("token");
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

    @Test
    void shouldBypassNegativeCacheWhenTtlIsZero() {
        HybridTokenBlacklistAdapter uncached = new HybridTokenBlacklistAdapter(
                database, redis, local,
                new TokenBlacklistProperties(1_000, 2_592_000_000L, 0L, 10L));
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token")).thenReturn(false, true);

        assertThat(uncached.isBlacklisted("token")).isFalse();
        assertThat(uncached.isBlacklisted("token")).isTrue();
        verify(database, times(2)).isBlacklisted("token");
    }

    @Test
    void shouldNotCreateTheNegativeCacheWhenTtlIsZero() throws Exception {
        HybridTokenBlacklistAdapter uncached = new HybridTokenBlacklistAdapter(
                database, redis, local,
                new TokenBlacklistProperties(1_000, 2_592_000_000L, 0L, 10L));

        java.lang.reflect.Field cache =
                HybridTokenBlacklistAdapter.class.getDeclaredField("notRevoked");
        cache.setAccessible(true);

        assertThat(cache.get(uncached)).isNull();
    }

    @Test
    void shouldCreateTheNegativeCacheForAPositiveTtl() throws Exception {
        java.lang.reflect.Field cache =
                HybridTokenBlacklistAdapter.class.getDeclaredField("notRevoked");
        cache.setAccessible(true);

        assertThat(cache.get(adapter)).isNotNull();
    }

    @Test
    void shouldReuseNegativeCacheAcrossLookups() {
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token")).thenReturn(false);

        assertThat(adapter.isBlacklisted("token")).isFalse();
        assertThat(adapter.isBlacklisted("token")).isFalse();

        verify(redis, times(2)).isBlacklisted("token");
        verify(database).isBlacklisted("token");
    }

    @Test
    void shouldReturnTrueFromLocalOverlayWithoutSharedLookups() {
        local.blacklist("token", 60_000);

        assertThat(adapter.isBlacklisted("token")).isTrue();

        verify(redis, never()).isBlacklisted("token");
        verify(database, never()).isBlacklisted("token");
    }

    @Test
    void shouldInvalidateDuringWritesSoAnInFlightReadCannotUseStaleCache() {
        TokenBlacklistAdapter localMock = mock(TokenBlacklistAdapter.class);
        HybridTokenBlacklistAdapter hybrid = new HybridTokenBlacklistAdapter(database, redis, localMock);
        when(redis.isBlacklisted("token")).thenReturn(false);
        when(database.isBlacklisted("token")).thenReturn(false, false, true);
        doAnswer(invocation -> {
            // At this point the pre-write invalidation has already run, so a
            // read must reach the database instead of the stale negative entry.
            // Running it inline keeps the ordering deterministic.
            assertThat(hybrid.isBlacklisted("token")).isFalse();
            verify(database, times(2)).isBlacklisted("token");
            return null;
        }).when(database).blacklist("token", 60_000);

        // Populate the negative cache with the pre-revocation answer.
        assertThat(hybrid.isBlacklisted("token")).isFalse();

        hybrid.blacklist("token", 60_000);

        // The in-write read repopulated the cache; the final invalidation must
        // clear it, otherwise this lookup would answer from the stale entry.
        assertThat(hybrid.isBlacklisted("token")).isTrue();
        verify(database, times(3)).isBlacklisted("token");
    }

    @Test
    void shouldSuppressRedisFailureWhenTheDatabaseWriteAlreadyFailed() {
        RevocationStoreUnavailableException databaseFailure =
                new RevocationStoreUnavailableException(new RuntimeException("db down"));
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("redis down");
        doThrow(databaseFailure).when(database).blacklist("token", 60_000);
        doThrow(redisFailure).when(redis).blacklist("token", 60_000);

        assertThatThrownBy(() -> adapter.blacklist("token", 60_000))
                .isInstanceOf(RevocationStoreUnavailableException.class)
                .satisfies(thrown -> {
                    assertThat(thrown.getCause()).isSameAs(databaseFailure);
                    assertThat(databaseFailure.getSuppressed()).containsExactly(redisFailure);
                });
        assertThat(local.isBlacklisted("token")).isTrue();
    }

    @Test
    void shouldCleanExpiredOnTheLocalOverlay() {
        TokenBlacklistAdapter localMock = mock(TokenBlacklistAdapter.class);
        HybridTokenBlacklistAdapter hybrid = new HybridTokenBlacklistAdapter(database, redis, localMock);

        hybrid.cleanExpired();

        verify(database).cleanExpired();
        verify(redis).cleanExpired();
        verify(localMock).cleanExpired();
    }
}
