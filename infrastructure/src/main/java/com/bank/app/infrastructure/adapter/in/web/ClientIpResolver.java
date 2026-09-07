package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.user.application.port.out.ClientIpResolverPort;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver implements ClientIpResolverPort {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(ProxyProperties proxyProperties) {
        this.trustForwardedHeaders = proxyProperties.trustForwardedHeaders();
    }

    @Override
    public String resolveClientIp(String forwardedForHeader, String remoteAddr) {
        // Secure by default: X-Forwarded-For is attacker-controlled unless a
        // trusted reverse proxy overwrites it. Honor it only when the operator
        // explicitly declares such a proxy via app.proxy.trust-forwarded-headers.
        // Otherwise the TCP peer address is the only trustworthy signal, keeping
        // IP-based rate limiting, brute-force lockout and public-endpoint
        // idempotency keys effective.
        if (!trustForwardedHeaders) {
            return remoteAddr;
        }
        String ip = forwardedForHeader;
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return remoteAddr;
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex != -1 ? ip.substring(0, commaIndex).trim() : ip.trim();
    }
}
