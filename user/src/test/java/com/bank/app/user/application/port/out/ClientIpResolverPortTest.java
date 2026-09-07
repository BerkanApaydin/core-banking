package com.bank.app.user.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverPortTest {

    private static final String EXPECTED_IP = "203.0.113.195";

    private final ClientIpResolverPort resolver = (forwardedForHeader, remoteAddr) -> {
        String xff = forwardedForHeader;
        if (xff == null || xff.isEmpty() || "unknown".equalsIgnoreCase(xff)) {
            return remoteAddr;
        }
        int commaIndex = xff.indexOf(',');
        return commaIndex != -1 ? xff.substring(0, commaIndex).trim() : xff.trim();
    };

    @Test
    void shouldReturnXForwardedForWhenPresent() {
        String result = resolver.resolveClientIp(EXPECTED_IP, "10.0.0.1");

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldTakeFirstIpFromXForwardedForList() {
        String result = resolver.resolveClientIp("198.51.100.1, 10.0.0.1, 192.168.1.1", "10.0.0.9");

        assertEquals("198.51.100.1", result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsNull() {
        String result = resolver.resolveClientIp(null, EXPECTED_IP);

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsEmpty() {
        String result = resolver.resolveClientIp("", EXPECTED_IP);

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsUnknown() {
        String result = resolver.resolveClientIp("unknown", EXPECTED_IP);

        assertEquals(EXPECTED_IP, result);
    }
}
