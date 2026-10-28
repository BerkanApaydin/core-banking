package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.adapter.in.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * M-3: pre-authentication, IP-keyed limiter for the auth tier
 * ({@code /api/v1/auth/*}).
 *
 * <p>
 * Runs before authentication ({@code HIGHEST_PRECEDENCE + 1}) so no principal
 * exists yet — IP-keying is correct here (brute-force protection needs no
 * identity). Resource prefixes ({@code /accounts}, {@code /transfers},
 * {@code /admin}) are owned by {@link PrincipalRateLimitingFilter}, which keys
 * by authenticated principal: IP-keying them lets one NAT/botnet client starve
 * every legitimate user behind the same egress, and gives each bot IP an
 * independent budget on authenticated endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends AbstractRateLimitingFilter {

    private final ClientIpResolver clientIpResolver;

    public RateLimitingFilter(RateLimiter rateLimiter,
                              MessageSource messageSource,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper,
                              ClientIpResolver clientIpResolver) {
        super(rateLimiter, messageSource, rateLimitProperties, objectMapper);
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean ownsPrefix(String prefix) {
        return rateLimitProperties().isAuthTier(prefix);
    }

    @Override
    protected String backendLabel() {
        return "Rate limiting";
    }

    @Override
    protected String bucketKey(HttpServletRequest request, String matchedPrefix) {
        String ip = clientIpResolver.resolveClientIp(
                request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
        // Per-endpoint bucket per client ("ip|prefix"): login abuse must not
        // eat the refresh budget and vice versa. "|" is unambiguous here (it
        // appears in neither IPs nor matched prefixes).
        return ip + "|" + matchedPrefix;
    }
}
