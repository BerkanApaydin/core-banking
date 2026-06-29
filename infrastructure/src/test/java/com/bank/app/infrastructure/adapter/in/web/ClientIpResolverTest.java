package com.bank.app.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("ClientIpResolver")
class ClientIpResolverTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ClientIpResolver resolver;

    @Nested
    @DisplayName("resolveClientIp")
    class ResolveClientIp {

        @Test
        @DisplayName("should return X-Forwarded-For header when present")
        void shouldReturnXForwardedForHeader() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should return first IP from X-Forwarded-For list")
        void shouldReturnFirstIpFromList() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, 10.0.0.1, 192.168.1.1");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("198.51.100.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is unknown")
        void shouldFallbackWhenXForwardedForIsUnknown() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is absent")
        void shouldFallbackWhenNoHeader() {
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is empty")
        void shouldFallbackWhenEmptyHeader() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("should trim IP from X-Forwarded-For")
        void shouldTrimIp() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.195  ");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should handle case-insensitive Unknown value")
        void shouldHandleCaseInsensitiveUnknown() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("Unknown");
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            assertThat(resolver.resolveClientIp(request)).isEqualTo("10.0.0.1");
        }
    }
}
