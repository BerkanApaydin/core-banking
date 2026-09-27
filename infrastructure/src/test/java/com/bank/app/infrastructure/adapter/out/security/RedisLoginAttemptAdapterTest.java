package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class RedisLoginAttemptAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisLoginAttemptAdapter adapter;

    private static final String TEST_IP = "192.168.1.1";
    private static final String TEST_USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        adapter = new RedisLoginAttemptAdapter(redisTemplate, 5, 15);
    }

    private void stubOpsForValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shouldReturnFalseWhenIpNotPresent() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn(null);

        assertThat(adapter.isIpBlocked(TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenIpBelowThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn("3");

        assertThat(adapter.isIpBlocked(TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenIpAtThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn("5");

        assertThat(adapter.isIpBlocked(TEST_IP)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenIpAboveThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn("7");

        assertThat(adapter.isIpBlocked(TEST_IP)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenIpBlockingDisabled() {
        assertThat(new RedisLoginAttemptAdapter(redisTemplate, -1, 15).isIpBlocked(TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUsernameNotPresent() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn(null);

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUsernameBelowThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn("2");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUsernameAtThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn("5");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenUsernameAboveThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn("10");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUsernameBlockingDisabled() {
        assertThat(new RedisLoginAttemptAdapter(redisTemplate, -1, 15).isUsernameBlocked(TEST_USERNAME)).isFalse();
    }

    @Test
    void shouldRecordFailure() {
        when(redisTemplate.execute(any(DefaultRedisScript.class),
                eq(List.of("login_attempt:ip:" + TEST_IP, "login_attempt:user:" + TEST_USERNAME)),
                eq("900000"))).thenReturn(1L);

        adapter.recordFailure(TEST_IP, TEST_USERNAME);

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("login_attempt:ip:" + TEST_IP, "login_attempt:user:" + TEST_USERNAME)),
                eq("900000"));
    }

    @Test
    void shouldResetByIp() {
        adapter.reset(TEST_IP);

        verify(redisTemplate).delete("login_attempt:ip:" + TEST_IP);
    }

    @Test
    void shouldResetByUsername() {
        adapter.resetByUsername(TEST_USERNAME);

        verify(redisTemplate).delete("login_attempt:user:" + TEST_USERNAME);
    }

    @Test
    void shouldReturnWindowMinutes() {
        assertThat(adapter.getWindowMinutes()).isEqualTo(15);
    }

    @Test
    void shouldRejectNonPositiveWindowBecauseCountersMustExpire() {
        assertThatThrownBy(() -> new RedisLoginAttemptAdapter(redisTemplate, 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisLoginAttemptAdapter(redisTemplate, 5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnMaxAttempts() {
        assertThat(adapter.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void shouldFailClosedWhenIpAttemptCountCannotBeRead() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.isIpBlocked(TEST_IP))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void shouldFailClosedWhenUsernameAttemptCountCannotBeRead() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.isUsernameBlocked(TEST_USERNAME))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void shouldFailClosedWhenStoredAttemptCountIsCorrupt() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn("not-a-counter");

        assertThatThrownBy(() -> adapter.isIpBlocked(TEST_IP))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldFailClosedWhenFailureCannotBeRecorded() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(String.class)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.recordFailure(TEST_IP, TEST_USERNAME))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class);
    }

    @Test
    void shouldFailClosedWhenRedisScriptReturnsNoResult() {
        assertThatThrownBy(() -> adapter.recordFailure(TEST_IP, TEST_USERNAME))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailClosedWhenSuccessfulLoginCannotResetAttemptCount() {
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redisTemplate).delete("login_attempt:user:" + TEST_USERNAME);

        assertThatThrownBy(() -> adapter.resetByUsername(TEST_USERNAME))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class);
    }
}
