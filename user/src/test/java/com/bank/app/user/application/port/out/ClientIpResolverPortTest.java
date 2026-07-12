package com.bank.app.user.application.port.out;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class ClientIpResolverPortTest {

    private static final String EXPECTED_IP = "203.0.113.195";

    private final ClientIpResolverPort resolver = request -> {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isEmpty() || "unknown".equalsIgnoreCase(xff)) {
            return request.getRemoteAddr();
        }
        int commaIndex = xff.indexOf(',');
        return commaIndex != -1 ? xff.substring(0, commaIndex).trim() : xff.trim();
    };

    @Test
    void shouldReturnXForwardedForWhenPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(EXPECTED_IP);

        String result = resolver.resolveClientIp(request);

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldTakeFirstIpFromXForwardedForList() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, 10.0.0.1, 192.168.1.1");

        String result = resolver.resolveClientIp(request);

        assertEquals("198.51.100.1", result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(EXPECTED_IP);

        String result = resolver.resolveClientIp(request);

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn(EXPECTED_IP);

        String result = resolver.resolveClientIp(request);

        assertEquals(EXPECTED_IP, result);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenXForwardedForIsUnknown() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn(EXPECTED_IP);

        String result = resolver.resolveClientIp(request);

        assertEquals(EXPECTED_IP, result);
    }
}
