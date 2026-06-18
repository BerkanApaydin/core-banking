package com.bank.app.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedLoginAttemptServiceEdgeCaseTest {

    @Test
    void shouldBlockWhenMaxAttemptsIsZeroAndAttemptExists() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(0, 1);
        String ip = "10.0.0.1";

        assertFalse(service.isBlocked(ip));
        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));
    }

    @Test
    void shouldNeverBlockWhenMaxAttemptsIsNegative() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(-1, 1);
        String ip = "10.0.0.3";

        assertFalse(service.isBlocked(ip));
        service.recordFailure(ip);
        assertFalse(service.isBlocked(ip));
    }

    @Test
    void shouldHandleResetOnNonExistentIp() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);
        assertDoesNotThrow(() -> service.reset("10.0.0.4"));
    }

    @Test
    void shouldNotBlockMultipleIpsWhenNoneExceedLimit() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(3, 1);

        for (int i = 0; i < 10; i++) {
            String ip = "10.0.0." + i;
            service.recordFailure(ip);
            service.recordFailure(ip);
            assertFalse(service.isBlocked(ip), ip + " should not be blocked with 2 attempts");
        }
    }

    @Test
    void shouldHandleLargeMaxAttempts() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(Integer.MAX_VALUE, 1);
        String ip = "10.0.0.99";

        for (int i = 0; i < 1000; i++) {
            service.recordFailure(ip);
        }
        assertFalse(service.isBlocked(ip));
    }

    @Test
    void shouldHandleSmallWindowMinutes() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);
        String ip = "10.0.0.100";

        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip);
        }
        assertTrue(service.isBlocked(ip));
    }
}
