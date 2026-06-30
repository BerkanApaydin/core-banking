package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineLoginAttemptAdapterTest {

    @Test
    void shouldNotBlockWhenMaxAttemptsNegative() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(-1, 15);
        assertFalse(adapter.isIpBlocked("192.168.1.1"));
        assertFalse(adapter.isUsernameBlocked("user"));
    }

    @Test
    void shouldBlockIpAfterMaxAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(3, 15);
        assertFalse(adapter.isIpBlocked("192.168.1.1"));

        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.1", "user");

        assertTrue(adapter.isIpBlocked("192.168.1.1"));
    }

    @Test
    void shouldBlockUsernameAfterMaxAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(3, 15);
        assertFalse(adapter.isUsernameBlocked("user"));

        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.2", "user");
        adapter.recordFailure("192.168.1.3", "user");

        assertTrue(adapter.isUsernameBlocked("user"));
    }

    @Test
    void shouldResetIpAfterBlocking() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(2, 15);
        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.1", "user");
        assertTrue(adapter.isIpBlocked("192.168.1.1"));

        adapter.reset("192.168.1.1");
        assertFalse(adapter.isIpBlocked("192.168.1.1"));
    }

    @Test
    void shouldResetUsernameAfterBlocking() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(2, 15);
        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.2", "user");
        assertTrue(adapter.isUsernameBlocked("user"));

        adapter.resetByUsername("user");
        assertFalse(adapter.isUsernameBlocked("user"));
    }

    @Test
    void shouldReturnWindowMinutes() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(5, 30);
        assertEquals(30, adapter.getWindowMinutes());
    }

    @Test
    void shouldReturnMaxAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(5, 15);
        assertEquals(5, adapter.getMaxAttempts());
    }

    @Test
    void shouldNotBlockWhenWithinAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(5, 15);
        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.1", "user");
        adapter.recordFailure("192.168.1.1", "user");
        assertFalse(adapter.isIpBlocked("192.168.1.1"));
        assertFalse(adapter.isUsernameBlocked("user"));
    }

    @Test
    void shouldRecordFailureOnlyOnce() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(2, 15);
        adapter.recordFailure("192.168.1.1", "user");
        assertFalse(adapter.isIpBlocked("192.168.1.1"));
        assertFalse(adapter.isUsernameBlocked("user"));
    }
}
