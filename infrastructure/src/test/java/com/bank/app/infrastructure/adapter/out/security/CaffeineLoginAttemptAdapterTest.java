package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.security.LoginAttemptProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The guard has three budgets (see {@link LoginAttemptPort}): IP,
 * account+IP (tight) and account-only (loose, for distributed attacks).
 */
class CaffeineLoginAttemptAdapterTest {

    private static final String IP_A = "192.168.1.1";
    private static final String IP_B = "192.168.1.2";
    private static final String IP_C = "192.168.1.3";

    @Test
    void shouldNotBlockWhenMaxAttemptsNegative() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(-1, 15, 10000));
        assertFalse(adapter.isIpBlocked(IP_A));
        assertFalse(adapter.isUsernameBlocked("user", IP_A));
    }

    @Test
    void shouldBlockIpAfterMaxAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(3, 15, 10000));
        assertFalse(adapter.isIpBlocked(IP_A));

        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");

        assertTrue(adapter.isIpBlocked(IP_A));
    }

    @Test
    void shouldBlockAccountForTheFailingClientOnly() {
        // The tight budget is keyed on account+IP: three failures from one
        // client lock the account FOR THAT CLIENT, not for everyone. The old
        // account-only counter made a cheap denial of service possible
        // against a victim account.
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(3, 15, 10000));
        assertFalse(adapter.isUsernameBlocked("user", IP_A));

        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");

        assertTrue(adapter.isUsernameBlocked("user", IP_A));
        assertFalse(adapter.isUsernameBlocked("user", IP_B));
    }

    @Test
    void shouldBlockAccountGloballyOnlyAfterTheLooseBudget() {
        // Distributed attempt: every failure comes from a different client, so
        // no pair budget is exhausted — the account-only budget catches it,
        // at a much higher threshold.
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(
                new LoginAttemptProperties(2, 50, 2, 10000));

        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_B, "user");
        assertFalse(adapter.isUsernameBlocked("user", IP_C));

        for (int i = 0; i < 50; i++) {
            adapter.recordFailure(IP_C, "user");
        }
        assertTrue(adapter.isUsernameBlocked("user", IP_C));
    }

    @Test
    void shouldResetIpAfterBlocking() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(2, 15, 10000));
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        assertTrue(adapter.isIpBlocked(IP_A));

        adapter.reset(IP_A);
        assertFalse(adapter.isIpBlocked(IP_A));
    }

    @Test
    void shouldResetUsernameAfterBlocking() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(2, 15, 10000));
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        assertTrue(adapter.isUsernameBlocked("user", IP_A));

        adapter.resetByUsername("user");
        assertFalse(adapter.isUsernameBlocked("user", IP_A));
    }

    @Test
    void shouldReturnWindowMinutes() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(5, 30, 10000));
        assertEquals(30, adapter.getWindowMinutes());
    }

    @Test
    void shouldNotBlockWhenWithinAttempts() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(5, 15, 10000));
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        adapter.recordFailure(IP_A, "user");
        assertFalse(adapter.isIpBlocked(IP_A));
        assertFalse(adapter.isUsernameBlocked("user", IP_A));
    }

    @Test
    void shouldRecordFailureOnlyOnce() {
        CaffeineLoginAttemptAdapter adapter = new CaffeineLoginAttemptAdapter(new LoginAttemptProperties(2, 15, 10000));
        adapter.recordFailure(IP_A, "user");
        assertFalse(adapter.isIpBlocked(IP_A));
        assertFalse(adapter.isUsernameBlocked("user", IP_A));
    }
}
