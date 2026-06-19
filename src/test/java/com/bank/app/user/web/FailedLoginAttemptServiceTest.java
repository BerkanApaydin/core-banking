package com.bank.app.user.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedLoginAttemptServiceTest {

    private final FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);

    @Test
    void shouldNotBlockOnFirstAttempt() {
        assertFalse(service.isBlocked("192.168.1.1"));
    }

    @Test
    void shouldBlockAfterMultipleFailures() {
        String ip = "192.168.1.2";

        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);

        assertTrue(service.isBlocked(ip));
    }

    @Test
    void shouldAllowAccessAfterReset() {
        String ip = "192.168.1.3";

        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);
        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));

        service.reset(ip);
        assertFalse(service.isBlocked(ip));
    }

    @Test
    void shouldNotAffectOtherIps() {
        String blockedIp = "192.168.1.4";
        String otherIp = "192.168.1.5";

        service.recordFailure(blockedIp);
        service.recordFailure(blockedIp);
        service.recordFailure(blockedIp);
        service.recordFailure(blockedIp);
        service.recordFailure(blockedIp);

        assertTrue(service.isBlocked(blockedIp));
        assertFalse(service.isBlocked(otherIp));
    }

    @Test
    void shouldTrackAttemptsPerIpIndependently() {
        String ip1 = "192.168.1.6";
        String ip2 = "192.168.1.7";

        service.recordFailure(ip1);
        service.recordFailure(ip1);

        assertFalse(service.isBlocked(ip1));
        assertFalse(service.isBlocked(ip2));

        service.recordFailure(ip1);
        service.recordFailure(ip1);
        service.recordFailure(ip1);

        assertTrue(service.isBlocked(ip1));
        assertFalse(service.isBlocked(ip2));
    }

    @Test
    void shouldResetOnlySpecifiedIp() {
        String ip1 = "192.168.1.8";
        String ip2 = "192.168.1.9";

        service.recordFailure(ip1);
        service.recordFailure(ip1);
        service.recordFailure(ip1);
        service.recordFailure(ip1);
        service.recordFailure(ip1);

        service.recordFailure(ip2);
        service.recordFailure(ip2);
        service.recordFailure(ip2);
        service.recordFailure(ip2);
        service.recordFailure(ip2);

        assertTrue(service.isBlocked(ip1));
        assertTrue(service.isBlocked(ip2));

        service.reset(ip1);

        assertFalse(service.isBlocked(ip1));
        assertTrue(service.isBlocked(ip2));
    }

    @Test
    void shouldHandleCustomSettings() {
        FailedLoginAttemptService custom = new FailedLoginAttemptService(3, 5);
        String ip = "192.168.1.10";

        assertFalse(custom.isBlocked(ip));
        custom.recordFailure(ip);
        custom.recordFailure(ip);
        custom.recordFailure(ip);
        assertTrue(custom.isBlocked(ip));
        assertEquals(3, custom.getMaxAttempts());
        assertEquals(5, custom.getWindowMinutes());
    }
}
