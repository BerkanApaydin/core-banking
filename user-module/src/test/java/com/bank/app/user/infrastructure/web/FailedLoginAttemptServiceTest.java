package com.bank.app.user.infrastructure.web;

import com.bank.app.user.infrastructure.security.FailedLoginAttemptService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedLoginAttemptServiceTest {

    private final FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);

    @Test
    void shouldNotBlockOnFirstAttempt() {
        assertFalse(service.isIpBlocked("192.168.1.1"));
    }

    @Test
    void shouldBlockAfterMultipleFailures() {
        String ip = "192.168.1.2";

        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);

        assertTrue(service.isIpBlocked(ip));
    }

    @Test
    void shouldAllowAccessAfterReset() {
        String ip = "192.168.1.3";

        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        service.recordFailure(ip, "user" + ip);
        assertTrue(service.isIpBlocked(ip));

        service.reset(ip);
        assertFalse(service.isIpBlocked(ip));
    }

    @Test
    void shouldNotAffectOtherIps() {
        String blockedIp = "192.168.1.4";
        String otherIp = "192.168.1.5";

        service.recordFailure(blockedIp, "user" + blockedIp);
        service.recordFailure(blockedIp, "user" + blockedIp);
        service.recordFailure(blockedIp, "user" + blockedIp);
        service.recordFailure(blockedIp, "user" + blockedIp);
        service.recordFailure(blockedIp, "user" + blockedIp);

        assertTrue(service.isIpBlocked(blockedIp));
        assertFalse(service.isIpBlocked(otherIp));
    }

    @Test
    void shouldTrackAttemptsPerIpIndependently() {
        String ip1 = "192.168.1.6";
        String ip2 = "192.168.1.7";

        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);

        assertFalse(service.isIpBlocked(ip1));
        assertFalse(service.isIpBlocked(ip2));

        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);

        assertTrue(service.isIpBlocked(ip1));
        assertFalse(service.isIpBlocked(ip2));
    }

    @Test
    void shouldResetOnlySpecifiedIp() {
        String ip1 = "192.168.1.8";
        String ip2 = "192.168.1.9";

        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);
        service.recordFailure(ip1, "user" + ip1);

        service.recordFailure(ip2, "user" + ip2);
        service.recordFailure(ip2, "user" + ip2);
        service.recordFailure(ip2, "user" + ip2);
        service.recordFailure(ip2, "user" + ip2);
        service.recordFailure(ip2, "user" + ip2);

        assertTrue(service.isIpBlocked(ip1));
        assertTrue(service.isIpBlocked(ip2));

        service.reset(ip1);

        assertFalse(service.isIpBlocked(ip1));
        assertTrue(service.isIpBlocked(ip2));
    }

    @Test
    void shouldHandleCustomSettings() {
        FailedLoginAttemptService custom = new FailedLoginAttemptService(3, 5);
        String ip = "192.168.1.10";

        assertFalse(custom.isIpBlocked(ip));
        custom.recordFailure(ip, "user" + ip);
        custom.recordFailure(ip, "user" + ip);
        custom.recordFailure(ip, "user" + ip);
        assertTrue(custom.isIpBlocked(ip));
        assertEquals(3, custom.getMaxAttempts());
        assertEquals(5, custom.getWindowMinutes());
    }
}
