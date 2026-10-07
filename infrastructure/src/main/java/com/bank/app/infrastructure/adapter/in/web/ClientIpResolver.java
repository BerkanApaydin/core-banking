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
        // trusted reverse proxy sits in front. Honor it only when the operator
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
        // Last entry wins, not first: append-semantics proxies (nginx-ingress
        // default $proxy_add_x_forwarded_for) append the downstream peer, so the
        // last entry is the address the trusted edge actually saw. The first
        // entry is attacker-controlled — trusting it would let anyone mint
        // fresh rate-limit/brute-force buckets with spoofed IPs. Entries equal
        // to "unknown" (some proxies emit them for obfuscated hops) are skipped
        // from the right. Requires a single-ingress topology: with a CDN or a
        // multi-hop chain in front, the last entry is the outermost proxy, not
        // the client — revisit before adding hops (see k8s/ingress.yaml).
        String[] parts = ip.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return remoteAddr;
    }
}
