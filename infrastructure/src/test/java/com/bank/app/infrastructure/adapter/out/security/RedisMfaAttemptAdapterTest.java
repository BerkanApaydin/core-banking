package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.config.MfaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Production pins the Redis MFA budget: every replica must see the same
 * failed-code count, otherwise an attacker spreads attempts across pods and
 * the account lockout never fires. These tests pin the fail-closed contract
 * (a Redis problem must never look like a free attempt) and the exact key/TTL
 * shape the atomic INCR+PEXPIRE script relies on.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
@DisplayName("RedisMfaAttemptAdapter")
class RedisMfaAttemptAdapterTest {

    private static final Long USER_ID = 100L;
    private static final String KEY = "mfa_attempt:user:" + USER_ID;
    private static final String WINDOW_MILLIS = "900000";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisMfaAttemptAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisMfaAttemptAdapter(redisTemplate, props(5, 15));
    }

    private static MfaProperties props(int maxAttempts, int windowMinutes) {
        return new MfaProperties("issuer", "", "", 30, 6, 1, 15,
                maxAttempts, windowMinutes, 10000);
    }

    private void stubOpsForValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("does not consult Redis for a null user or a disabled guard")
    void shouldShortCircuitNullUserAndDisabledGuard() {
        RedisMfaAttemptAdapter disabled = new RedisMfaAttemptAdapter(redisTemplate, props(-1, 15));

        assertThat(adapter.isBlocked(null)).isFalse();
        assertThat(disabled.isBlocked(USER_ID)).isFalse();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("is not blocked while no counter exists")
    void shouldNotBlockWhenCounterIsAbsent() {
        stubOpsForValue();
        when(valueOps.get(KEY)).thenReturn(null);

        assertThat(adapter.isBlocked(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("is not blocked below the threshold")
    void shouldNotBlockBelowThreshold() {
        stubOpsForValue();
        when(valueOps.get(KEY)).thenReturn("4");

        assertThat(adapter.isBlocked(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("blocks at and above the threshold")
    void shouldBlockAtAndAboveThreshold() {
        stubOpsForValue();
        when(valueOps.get(KEY)).thenReturn("5");

        assertThat(adapter.isBlocked(USER_ID)).isTrue();

        when(valueOps.get(KEY)).thenReturn("6");

        assertThat(adapter.isBlocked(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("a zero threshold blocks as soon as a counter exists")
    void shouldTreatZeroThresholdAsImmediateLockout() {
        RedisMfaAttemptAdapter zero = new RedisMfaAttemptAdapter(redisTemplate, props(0, 15));
        stubOpsForValue();
        when(valueOps.get(KEY)).thenReturn("0");

        assertThat(zero.isBlocked(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("fails closed when the stored counter is corrupt")
    void shouldFailClosedOnCorruptCounter() {
        stubOpsForValue();
        when(valueOps.get(KEY)).thenReturn("not-a-counter");

        assertThatThrownBy(() -> adapter.isBlocked(USER_ID))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("fails closed when Redis cannot read the counter")
    void shouldFailClosedWhenReadFails() {
        stubOpsForValue();
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.isBlocked(USER_ID))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("records a failure against the per-user key with the configured TTL")
    void shouldRecordFailureWithAtomicScript() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS)))
                .thenReturn(1L);

        adapter.recordFailure(USER_ID);

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS));
    }

    @Test
    @DisplayName("ignores a null user instead of creating a shared null key")
    void shouldIgnoreNullUserOnRecordFailure() {
        adapter.recordFailure(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("fails closed when the script reports no result")
    void shouldFailClosedWhenScriptReturnsNull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS)))
                .thenReturn(null);

        assertThatThrownBy(() -> adapter.recordFailure(USER_ID))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fails closed when Redis cannot increment the counter")
    void shouldFailClosedWhenRecordFails() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.recordFailure(USER_ID))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("clears the counter after a successful code")
    void shouldResetCounter() {
        adapter.reset(USER_ID);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("ignores a null user on reset")
    void shouldIgnoreNullUserOnReset() {
        adapter.reset(null);

        verifyNoInteractions(redisTemplate);
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    @DisplayName("fails closed when Redis cannot delete the counter")
    void shouldFailClosedWhenResetFails() {
        when(redisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.reset(USER_ID))
                .isInstanceOf(LoginAttemptStoreUnavailableException.class);
    }

    @Test
    @DisplayName("exposes the configured lockout window")
    void shouldReturnWindowMinutes() {
        assertThat(adapter.getWindowMinutes()).isEqualTo(15);
    }
}
