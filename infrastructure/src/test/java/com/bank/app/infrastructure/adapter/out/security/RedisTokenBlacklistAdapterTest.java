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
import static org.mockito.Mockito.*;

@DisplayName("RedisTokenBlacklistAdapter")
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistAdapterTest {

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
        @DisplayName("should store token with prefix and TTL")
        void shouldStoreTokenWithPrefix() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            adapter.blacklist("token-1", 60000L);

            verify(valueOperations).set("token_blacklist:token-1", "blacklisted", 60000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        @Test
        @DisplayName("should return true when key exists")
        void shouldReturnTrueWhenKeyExists() {
            when(redisTemplate.hasKey("token_blacklist:token-1")).thenReturn(true);

            assertThat(adapter.isBlacklisted("token-1")).isTrue();
        }

        @Test
        @DisplayName("should return false when key does not exist")
        void shouldReturnFalseWhenKeyDoesNotExist() {
            when(redisTemplate.hasKey("token_blacklist:token-1")).thenReturn(false);

            assertThat(adapter.isBlacklisted("token-1")).isFalse();
        }

        @Test
        @DisplayName("should return false when hasKey returns null")
        void shouldReturnFalseWhenHasKeyReturnsNull() {
            when(redisTemplate.hasKey("token_blacklist:token-1")).thenReturn(null);

            assertThat(adapter.isBlacklisted("token-1")).isFalse();
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
