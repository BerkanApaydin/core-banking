package com.bank.app.user.infrastructure.web;

import com.bank.app.user.infrastructure.security.FailedLoginAttemptService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedLoginAttemptServiceEdgeCaseTest {

    @Test
    void shouldBlockWhenMaxAttemptsIsZeroAndAttemptExists() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(0, 1);
        String ip = "10.0.0.1";

        assertFalse(service.isIpBlocked(ip));
        service.recordFailure(ip, "user");

        assertTrue(service.isIpBlocked(ip));
    }

    @Test
    void shouldNeverBlockWhenMaxAttemptsIsNegative() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(-1, 1);
        String ip = "10.0.0.2";

        service.recordFailure(ip, "user");
        service.recordFailure(ip, "user");
        service.recordFailure(ip, "user");

        assertFalse(service.isIpBlocked(ip));
    }

    @Test
    void shouldHandleDifferentIpsIndependently() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(2, 1);
        String ip1 = "10.0.0.7";
        String ip2 = "10.0.0.8";

        service.recordFailure(ip1, "user1");
        assertFalse(service.isIpBlocked(ip1));
        assertFalse(service.isIpBlocked(ip2));

        service.recordFailure(ip1, "user1");
        assertTrue(service.isIpBlocked(ip1));
        assertFalse(service.isIpBlocked(ip2));
    }

    @Test
    void shouldResetBlockedIp() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(3, 1);
        String ip = "10.0.0.4";

        service.recordFailure(ip, "user");
        service.recordFailure(ip, "user");
        service.recordFailure(ip, "user");
        assertTrue(service.isIpBlocked(ip));

        service.reset(ip);
        assertFalse(service.isIpBlocked(ip));
    }

    @Test
    void shouldRespectMaxAttemptsSetting() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(Integer.MAX_VALUE, 1);
        String ip = "10.0.0.5";

        for (int i = 0; i < 100; i++) {
            service.recordFailure(ip, "user");
        }
        assertFalse(service.isIpBlocked(ip));
    }

    @Test
    void shouldBlockExactlyAtMaxAttempts() {
        FailedLoginAttemptService service = new FailedLoginAttemptService(5, 1);
        String ip = "10.0.0.6";

        assertFalse(service.isIpBlocked(ip));
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip, "user");
        }
        assertTrue(service.isIpBlocked(ip));
    }
}
