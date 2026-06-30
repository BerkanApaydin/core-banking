package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
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
        stubOpsForValue();
        adapter.recordFailure(TEST_IP, TEST_USERNAME);

        verify(valueOps).increment("login_attempt:ip:" + TEST_IP);
        verify(redisTemplate).expire("login_attempt:ip:" + TEST_IP, 15, java.util.concurrent.TimeUnit.MINUTES);
        verify(valueOps).increment("login_attempt:user:" + TEST_USERNAME);
        verify(redisTemplate).expire("login_attempt:user:" + TEST_USERNAME, 15, java.util.concurrent.TimeUnit.MINUTES);
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
    void shouldReturnMaxAttempts() {
        assertThat(adapter.getMaxAttempts()).isEqualTo(5);
    }
}
