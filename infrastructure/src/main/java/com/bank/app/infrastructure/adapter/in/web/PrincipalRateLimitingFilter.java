package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * M-3: principal-keyed second bucket for authenticated resource prefixes.
 *
 * <p>
 * {@link RateLimitingFilter} (pre-auth, IP-keyed) owns only
 * {@code /api/v1/auth/*}. This filter owns the resource prefixes
 * ({@code /api/v1/accounts}, {@code /transfers}, {@code /admin}) and keys by
 * authenticated principal ({@code principal:&lt;userId|username&gt;|prefix}).
 * Two problems fixed:
 * <ul>
 * <li>Availability: one malicious client behind a corporate NAT/CGNAT no
 * longer eats every legitimate user's budget behind the same egress.</li>
 * <li>Abuse: a botnet no longer gets an independent per-IP budget on
 * authenticated endpoints; each compromised account has its own budget.</li>
 * </ul>
 *
 * <p>
 * Ordering: {@code @Order(200)} runs AFTER Spring Security's filter chain
 * (order -100), so {@code SecurityContextHolder} already holds the JWT
 * principal established by {@code JwtAuthenticationFilter}. Unauthenticated
 * requests never reach here (the security chain rejects them with 401 first),
 * so there is intentionally no IP fallback — an absent principal means the
 * request is public or already rejected, and limiting it here would re-add
 * the NAT problem this filter removes.
 */
@Component
@Order(200)
public class PrincipalRateLimitingFilter extends AbstractRateLimitingFilter {

    public PrincipalRateLimitingFilter(RateLimiter rateLimiter,
            MessageSource messageSource,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper) {
        super(rateLimiter, messageSource, rateLimitProperties, objectMapper);
    }

    @Override
    protected boolean ownsPrefix(String prefix) {
        return !rateLimitProperties().isAuthTier(prefix);
    }

    @Override
    protected String backendLabel() {
        return "Principal rate limiting";
    }

    @Override
    protected String bucketKey(HttpServletRequest request, String matchedPrefix) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalKey = principalKey(authentication);
        if (principalKey == null) {
            // No authenticated principal (public path or already-rejected
            // request): nothing principal-keyed to enforce. Pass through —
            // the pre-auth IP filter and the security chain own this case.
            return null;
        }
        return "principal:" + principalKey + "|" + matchedPrefix;
    }

    private static String principalKey(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedPrincipalPort port) {
            Long userId = port.getAuthenticatedUserId();
            if (userId != null) {
                return String.valueOf(userId);
            }
            String username = port.getAuthenticatedUsername();
            if (username != null && !username.isBlank()) {
                return "name:" + username;
            }
        }
        // Anonymous / test principals (@WithMockUser): fall back to the
        // authentication name, which is still per-identity (NAT-safe).
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return "name:" + name;
    }
}
