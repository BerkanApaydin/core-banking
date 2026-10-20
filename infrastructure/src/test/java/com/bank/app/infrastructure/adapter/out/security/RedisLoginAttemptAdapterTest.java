package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.infrastructure.adapter.in.security.LoginAttemptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
        adapter = new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(5, 15, 10000));
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
        assertThat(new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(-1, 15, 10000)).isIpBlocked(TEST_IP)).isFalse();
    }

    @Test
    void shouldBlockIpWhenMaxAttemptsIsZeroAndCountPresent() {
        var zeroThreshold = new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(0, 15, 10000));
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn("0");

        assertThat(zeroThreshold.isIpBlocked(TEST_IP)).isTrue();
    }

    @Test
    void shouldNotBlockIpWhenMaxAttemptsIsZeroAndNoCount() {
        var zeroThreshold = new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(0, 15, 10000));
        stubOpsForValue();
        when(valueOps.get("login_attempt:ip:" + TEST_IP)).thenReturn(null);

        assertThat(zeroThreshold.isIpBlocked(TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUsernameNotPresent() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP)).thenReturn(null);
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn(null);

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME, TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUsernameBelowThreshold() {
        stubOpsForValue();
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP)).thenReturn("2");
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn("2");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME, TEST_IP)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUsernameAtThreshold() {
        // The tight budget is keyed on account+IP: the client that burned it
        // is the one that is blocked. A different client keeps its budget.
        stubOpsForValue();
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP)).thenReturn("5");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME, TEST_IP)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenAccountBudgetIsExhausted() {
        // The loose account-only budget catches a distributed attempt that
        // never exhausts any single pair bucket.
        stubOpsForValue();
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP)).thenReturn("1");
        when(valueOps.get("login_attempt:user:" + TEST_USERNAME)).thenReturn("50");

        assertThat(adapter.isUsernameBlocked(TEST_USERNAME, TEST_IP)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUsernameBlockingDisabled() {
        assertThat(new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(-1, 15, 10000)).isUsernameBlocked(TEST_USERNAME, TEST_IP)).isFalse();
    }

    @Test
    void shouldBlockUsernameWhenMaxAttemptsIsZeroAndCountPresent() {
        var zeroThreshold = new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(0, 15, 10000));
        stubOpsForValue();
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP)).thenReturn("0");

        assertThat(zeroThreshold.isUsernameBlocked(TEST_USERNAME, TEST_IP)).isTrue();
    }

    @Test
    void shouldReturnFalseForNullUsernameEvenWithZeroThreshold() {
        var zeroThreshold = new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(0, 15, 10000));

        assertThat(zeroThreshold.isUsernameBlocked(null, TEST_IP)).isFalse();
    }

    @Test
    void shouldRecordFailure() {
        // Three budgets are incremented in one atomic script: the IP bucket,
        // the account+IP bucket and the account-only bucket.
        when(redisTemplate.execute(any(DefaultRedisScript.class),
                eq(List.of("login_attempt:ip:" + TEST_IP,
                        "login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP,
                        "login_attempt:user:" + TEST_USERNAME)),
                eq("900000"))).thenReturn(1L);

        adapter.recordFailure(TEST_IP, TEST_USERNAME);

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("login_attempt:ip:" + TEST_IP,
                        "login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP,
                        "login_attempt:user:" + TEST_USERNAME)),
                eq("900000"));
    }

    @Test
    void shouldResetByIp() {
        adapter.reset(TEST_IP);

        verify(redisTemplate).delete("login_attempt:ip:" + TEST_IP);
    }

    @Test
    void shouldResetByUsername() {
        // The account+IP buckets carry the account in the key prefix, so they
        // are cleared by a SCAN (never KEYS: KEYS blocks the Redis main
        // thread for the whole keyspace).
        Cursor<String> cursor = emptyCursor();
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        adapter.resetByUsername(TEST_USERNAME);

        verify(redisTemplate).delete("login_attempt:user:" + TEST_USERNAME);
        verify(redisTemplate).scan(any(ScanOptions.class));
    }

    @Test
    void shouldEscapeGlobMetacharactersWhenResettingByUsername() {
        // A legacy username containing glob syntax must be matched literally;
        // otherwise its reset could delete every account's pair bucket.
        Cursor<String> cursor = emptyCursor();
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        adapter.resetByUsername("a*b?c[d]e\\f");

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(options.capture());
        assertThat(options.getValue().getPattern())
                .isEqualTo("login_attempt:account_ip:a\\*b\\?c\\[d\\]e\\\\f|*");
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> emptyCursor() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        return cursor;
    }

    @Test
    void shouldReturnWindowMinutes() {
        assertThat(adapter.getWindowMinutes()).isEqualTo(15);
    }

    @Test
    void shouldRejectNonPositiveWindowBecauseCountersMustExpire() {
        assertThatThrownBy(() -> new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(5, 0, 10000)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisLoginAttemptAdapter(redisTemplate, new LoginAttemptProperties(5, -1, 10000)))
                .isInstanceOf(IllegalArgumentException.class);
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
        when(valueOps.get("login_attempt:account_ip:" + TEST_USERNAME + "|" + TEST_IP))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.isUsernameBlocked(TEST_USERNAME, TEST_IP))
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
