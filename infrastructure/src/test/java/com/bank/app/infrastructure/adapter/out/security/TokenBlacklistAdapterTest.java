package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        void shouldExpireEntriesAfterTtl() throws InterruptedException {
            // Margins matter: with a 1ms TTL any CI scheduling hiccup between
            // put and get flakes the first assertion (surefire runs classes in
            // parallel). 50ms TTL + 200ms sleep keeps the same semantics.
            TokenBlacklistAdapter shortLived =
                    new TokenBlacklistAdapter(new TokenBlacklistProperties(50L, 10_000L));
            shortLived.blacklist("short-token", 50L);
            assertThat(shortLived.isBlacklisted("short-token")).isTrue();

            Thread.sleep(200);

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
