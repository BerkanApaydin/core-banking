package com.bank.app.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedLoginAttemptServiceTest {

    private final FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);

    @Test
    void shouldNotBlockOnFirstAttempt() {
        assertFalse(service.isBlocked("192.168.1.1"));
    }

    @Test
    void shouldBlockAfterMaxAttempts() {
        String ip = "10.0.0.1";

        for (int i = 0; i < 5; i++) {
            assertFalse(service.isBlocked(ip), "not blocked before attempt " + (i + 1));
            service.recordFailure(ip);
        }

        assertTrue(service.isBlocked(ip));
    }

    @Test
    void shouldAllowAfterReset() {
        String ip = "10.0.0.2";

        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip);
        }
        assertTrue(service.isBlocked(ip));

        service.reset(ip);
        assertFalse(service.isBlocked(ip));
    }

    @Test
    void shouldTrackIpsIndependently() {
        String ip1 = "10.0.0.3";
        String ip2 = "10.0.0.4";

        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip1);
        }
        assertTrue(service.isBlocked(ip1));
        assertFalse(service.isBlocked(ip2));
    }

    @Test
    void shouldRecordSingleFailure() {
        service.recordFailure("10.0.0.5");
        assertFalse(service.isBlocked("10.0.0.5"));
    }

    @Test
    void shouldReturnConfiguredValues() {
        FailedLoginAttemptService custom = new FailedLoginAttemptService(3, 5);
        assertEquals(3, custom.getMaxAttempts());
        assertEquals(5, custom.getWindowMinutes());
    }

    @Test
    void shouldBlockExactlyAtMaxAttempts() {
        String ip = "10.0.0.6";

        for (int i = 0; i < 3; i++) {
            service.recordFailure(ip);
        }
        assertFalse(service.isBlocked(ip), "not blocked at 3 attempts with max=5");

        service.recordFailure(ip);
        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));
    }

    @Test
    void shouldNotBlockDifferentIpWithSameCount() {
        String ip = "10.0.0.7";

        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip);
        }
        assertTrue(service.isBlocked(ip));
        assertFalse(service.isBlocked("10.0.0.8"));
    }
}
