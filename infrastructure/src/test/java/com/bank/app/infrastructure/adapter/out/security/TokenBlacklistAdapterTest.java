package com.bank.app.infrastructure.adapter.out.security;

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
        adapter = new TokenBlacklistAdapter();
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
}
