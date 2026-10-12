package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("TokenBlacklistAdapter")
class TokenBlacklistAdapterTest {

    private TokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TokenBlacklistAdapter(new TokenBlacklistProperties(1_000L, 2_592_000_000L));
    }

    @Nested
    @DisplayName("blacklist")
    class Blacklist {

        @Test
        @DisplayName("should mark token as blacklisted")
        void shouldBlacklistToken() {
            adapter.blacklist("token-1", 60_000L);
            assertThat(adapter.isBlacklisted("token-1")).isTrue();
        }

        @Test
        @DisplayName("should handle multiple tokens")
        void shouldHandleMultipleTokens() {
            adapter.blacklist("token-a", 60_000L);
            adapter.blacklist("token-b", 60_000L);
            assertThat(adapter.isBlacklisted("token-a")).isTrue();
            assertThat(adapter.isBlacklisted("token-b")).isTrue();
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        @Test
        @DisplayName("should return false for unknown token")
        void shouldReturnFalseForUnknownToken() {
            assertThat(adapter.isBlacklisted("unknown")).isFalse();
        }

        @Test
        @DisplayName("should return false for expired token")
        void shouldReturnFalseForExpiredToken() {
            adapter.blacklist("expired-token", -1L);
            assertThat(adapter.isBlacklisted("expired-token")).isFalse();
        }

        @Test
        @DisplayName("should expire entries after their TTL elapses")
    void shouldExpireEntriesAfterTtl() {
        // Margins matter: with a 1ms TTL any CI scheduling hiccup between
        // put and get flakes the first assertion (surefire runs classes in
        // parallel). 50ms TTL + bounded await keeps the same semantics (D19):
        // polls until expiry is observed instead of sleeping a fixed window.
        TokenBlacklistAdapter shortLived =
                new TokenBlacklistAdapter(new TokenBlacklistProperties(50L, 10_000L));
        shortLived.blacklist("short-token", 50L);
        assertThat(shortLived.isBlacklisted("short-token")).isTrue();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> !shortLived.isBlacklisted("short-token"));

        assertThat(shortLived.isBlacklisted("short-token")).isFalse();
    }
    }

    @Nested
    @DisplayName("cleanExpired")
    class CleanExpired {

        @Test
        @DisplayName("should remove expired tokens")
        void shouldRemoveExpiredTokens() {
            adapter.blacklist("valid", 60_000L);
            adapter.blacklist("expired", -1L);

            adapter.cleanExpired();

            assertThat(adapter.isBlacklisted("valid")).isTrue();
            assertThat(adapter.isBlacklisted("expired")).isFalse();
        }

        @Test
        @DisplayName("should handle empty blacklist")
        void shouldHandleEmptyBlacklist() {
            adapter.cleanExpired();
            assertThat(adapter.isBlacklisted("any")).isFalse();
        }
    }

    @Nested
    @DisplayName("expiry boundary")
    class ExpiryBoundary {

        @Test
        @DisplayName("should ignore zero expiry like negative expiry")
        void shouldIgnoreZeroExpiry() {
            adapter.blacklist("zero-token", 0L);

            assertThat(adapter.isBlacklisted("zero-token")).isFalse();
        }

        @Test
        @DisplayName("should treat the exact deadline as expired")
        void shouldTreatExactDeadlineAsExpired() throws Exception {
            Clock start = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
            TokenBlacklistAdapter clocked = new TokenBlacklistAdapter(
                    new TokenBlacklistProperties(1_000L, 10_000L));
            clocked.setClockForTests(start);
            clocked.blacklist("exact-token", 1_000L);

            assertThat(clocked.isBlacklisted("exact-token")).isTrue();

            clocked.setClockForTests(Clock.fixed(Instant.parse("2026-01-01T00:00:01Z"), ZoneOffset.UTC));

            assertThat(clocked.isBlacklisted("exact-token")).isFalse();
            // The expired entry must be invalidated, not left in the cache.
            Field cacheField = TokenBlacklistAdapter.class.getDeclaredField("blacklist");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Cache<String, Long> cache = (Cache<String, Long>) cacheField.get(clocked);
            assertThat(cache.getIfPresent(TokenDigest.sha256Hex("exact-token"))).isNull();
        }

        @Test
        @DisplayName("should reset to system clock when cleared")
        void shouldResetClockWhenCleared() {
            TokenBlacklistAdapter clocked = new TokenBlacklistAdapter(
                    new TokenBlacklistProperties(1_000L, 10_000L));
            clocked.setClockForTests(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
            clocked.setClockForTests(null);

            clocked.blacklist("reset-token", 60_000L);

            assertThat(clocked.isBlacklisted("reset-token")).isTrue();
        }
    }

    @Nested
    @DisplayName("ttl bounds")
    class TtlBounds {

        @Test
        @DisplayName("should clamp tiny TTL up to the configured minimum")
        void shouldClampToMinTtl() {
            TokenBlacklistAdapter bounded = new TokenBlacklistAdapter(
                    new TokenBlacklistProperties(60_000L, 2_592_000_000L));
            bounded.blacklist("token", 1L);
            assertThat(bounded.isBlacklisted("token")).isTrue();
        }

        @Test
        @DisplayName("should respect a custom max TTL ceiling")
        void shouldRespectMaxTtl() {
            TokenBlacklistAdapter bounded = new TokenBlacklistAdapter(
                    new TokenBlacklistProperties(1_000L, 3_600_000L));
            bounded.blacklist("token", Long.MAX_VALUE / 2);
            assertThat(bounded.isBlacklisted("token")).isTrue();
        }
    }
}
