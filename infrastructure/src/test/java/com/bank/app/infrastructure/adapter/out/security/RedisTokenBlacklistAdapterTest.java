package com.bank.app.infrastructure.adapter.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("RedisTokenBlacklistAdapter")
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistAdapterTest {

    private static final String HASHED_TOKEN_1_KEY =
            "token_blacklist:sha256:3f08aace122ee2368432c1ca23a049bc640bafbf00fdf33a52429f38ba12dbf9";
    private static final String LEGACY_TOKEN_1_KEY = "token_blacklist:token-1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlacklistAdapter(redisTemplate);
    }

    @Nested
    @DisplayName("blacklist")
    class Blacklist {

        @Test
        @DisplayName("should store only the token hash with the original TTL")
        void shouldStoreOnlyTokenHash() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            adapter.blacklist("token-1", 60000L);

            verify(valueOperations).set(HASHED_TOKEN_1_KEY, "blacklisted", 60000L, TimeUnit.MILLISECONDS);
            verifyNoMoreInteractions(valueOperations);
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        // The lookup is a single Lua script: the hashed key and the legacy
        // key are checked in ONE round trip (two hasKey calls meant two
        // network round trips on the shared Lettuce connection, on every
        // authenticated request).
        private void stubLookup(Long result) {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class)))
                    .thenReturn(result);
        }

        @Test
        @DisplayName("should return true for a hashed key without checking the legacy key")
        void shouldReturnTrueWhenHashedKeyExists() {
            stubLookup(1L);

            assertThat(adapter.isBlacklisted("token-1")).isTrue();
        }

        @Test
        @DisplayName("should recognize a legacy raw-token key during migration")
        void shouldReturnTrueWhenLegacyKeyExists() {
            stubLookup(1L);

            assertThat(adapter.isBlacklisted("token-1")).isTrue();
        }

        @Test
        @DisplayName("should return false when key does not exist")
        void shouldReturnFalseWhenKeyDoesNotExist() {
            stubLookup(0L);

            assertThat(adapter.isBlacklisted("token-1")).isFalse();
        }

        @Test
        @DisplayName("should refuse an indeterminate lookup")
        void shouldRefuseNullLookupResult() {
            stubLookup(null);

            assertThatThrownBy(() -> adapter.isBlacklisted("token-1"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should check the legacy key at the exact maximum supported length")
        void shouldCheckLegacyKeyAtExactMaximumLength() {
            stubLookup(0L);

            assertThat(adapter.isBlacklisted("x".repeat(2048))).isFalse();

            verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), eq("1"));
        }

        @Test
        @DisplayName("should skip the legacy raw-token lookup for oversized input (SEC-05)")
        void shouldSkipLegacyLookupForOversizedToken() {
            String oversized = "x".repeat(2049);
            stubLookup(0L);

            assertThat(adapter.isBlacklisted(oversized)).isFalse();
            // The legacy key is not even part of the script's key list for an
            // oversized token: the script skips it via the flag argument.
            verify(redisTemplate, times(1)).execute(any(DefaultRedisScript.class), anyList(), eq("0"));
        }
    }

    @Test
    @DisplayName("checks the legacy key at the exact 2048-character boundary")
    void shouldCheckLegacyAtTheExactBoundaryFromTopLevel() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class)))
                .thenReturn(0L);

        assertThat(adapter.isBlacklisted("x".repeat(2048))).isFalse();

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), eq("1"));
    }

    @Test
    @DisplayName("skips the legacy key above the 2048-character boundary")
    void shouldSkipLegacyAboveTheBoundaryFromTopLevel() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class)))
                .thenReturn(0L);

        assertThat(adapter.isBlacklisted("x".repeat(2049))).isFalse();

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), eq("0"));
    }

    @Nested
    @DisplayName("cleanExpired")
    class CleanExpired {

        @Test
        @DisplayName("should do nothing as Redis handles TTL")
        void shouldDoNothing() {
            adapter.cleanExpired();

            verifyNoInteractions(redisTemplate);
        }
    }
}
