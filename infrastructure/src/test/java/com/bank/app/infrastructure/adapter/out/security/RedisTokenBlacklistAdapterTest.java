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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.TimeUnit;

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

        @Test
        @DisplayName("should return true for a hashed key without checking the legacy key")
        void shouldReturnTrueWhenHashedKeyExists() {
            when(redisTemplate.hasKey(HASHED_TOKEN_1_KEY)).thenReturn(true);

            assertThat(adapter.isBlacklisted("token-1")).isTrue();
            verify(redisTemplate, never()).hasKey(LEGACY_TOKEN_1_KEY);
        }

        @Test
        @DisplayName("should recognize a legacy raw-token key during migration")
        void shouldReturnTrueWhenLegacyKeyExists() {
            when(redisTemplate.hasKey(HASHED_TOKEN_1_KEY)).thenReturn(false);
            when(redisTemplate.hasKey(LEGACY_TOKEN_1_KEY)).thenReturn(true);

            assertThat(adapter.isBlacklisted("token-1")).isTrue();
        }

        @Test
        @DisplayName("should return false when key does not exist")
        void shouldReturnFalseWhenKeyDoesNotExist() {
            when(redisTemplate.hasKey(HASHED_TOKEN_1_KEY)).thenReturn(false);
            when(redisTemplate.hasKey(LEGACY_TOKEN_1_KEY)).thenReturn(false);

            assertThat(adapter.isBlacklisted("token-1")).isFalse();
        }

        @Test
        @DisplayName("should refuse an indeterminate hashed-key lookup")
        void shouldRefuseNullHashedKeyResult() {
            when(redisTemplate.hasKey(HASHED_TOKEN_1_KEY)).thenReturn(null);

            assertThatThrownBy(() -> adapter.isBlacklisted("token-1"))
                    .isInstanceOf(IllegalStateException.class);
            verify(redisTemplate, never()).hasKey(LEGACY_TOKEN_1_KEY);
        }

        @Test
        void shouldRefuseNullLegacyKeyResult() {
            when(redisTemplate.hasKey(HASHED_TOKEN_1_KEY)).thenReturn(false);
            when(redisTemplate.hasKey(LEGACY_TOKEN_1_KEY)).thenReturn(null);

            assertThatThrownBy(() -> adapter.isBlacklisted("token-1"))
                    .isInstanceOf(IllegalStateException.class);
        }
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
