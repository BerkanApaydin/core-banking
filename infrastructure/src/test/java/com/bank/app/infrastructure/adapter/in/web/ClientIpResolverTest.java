package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpResolver")
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(new ProxyProperties(true));

    @Nested
    @DisplayName("resolveClientIp")
    class ResolveClientIp {

        @Test
        @DisplayName("should return X-Forwarded-For header when present")
        void shouldReturnXForwardedForHeader() {
            assertThat(resolver.resolveClientIp("203.0.113.195", "10.0.0.1")).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should return first IP from X-Forwarded-For list")
        void shouldReturnFirstIpFromList() {
            assertThat(resolver.resolveClientIp("198.51.100.1, 10.0.0.1, 192.168.1.1", "10.0.0.9"))
                    .isEqualTo("198.51.100.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is unknown")
        void shouldFallbackWhenXForwardedForIsUnknown() {
            assertThat(resolver.resolveClientIp("unknown", "127.0.0.1")).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is absent")
        void shouldFallbackWhenNoHeader() {
            assertThat(resolver.resolveClientIp(null, "10.0.0.1")).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should return remote address when X-Forwarded-For is empty")
        void shouldFallbackWhenEmptyHeader() {
            assertThat(resolver.resolveClientIp("", "192.168.1.1")).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("should trim IP from X-Forwarded-For")
        void shouldTrimIp() {
            assertThat(resolver.resolveClientIp("  203.0.113.195  ", "10.0.0.1")).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should handle case-insensitive Unknown value")
        void shouldHandleCaseInsensitiveUnknown() {
            assertThat(resolver.resolveClientIp("Unknown", "10.0.0.1")).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("untrusted proxy headers (default)")
    class DistrustedHeaders {

        private final ClientIpResolver distrusting = new ClientIpResolver(new ProxyProperties(false));

        @Test
        @DisplayName("should ignore spoofable X-Forwarded-For and use remote address")
        void shouldIgnoreSpoofedHeader() {
            assertThat(distrusting.resolveClientIp("203.0.113.195", "10.0.0.1")).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should ignore X-Forwarded-For list and use remote address")
        void shouldIgnoreHeaderList() {
            assertThat(distrusting.resolveClientIp("198.51.100.1, 10.0.0.1", "10.0.0.9"))
                    .isEqualTo("10.0.0.9");
        }
    }
}
