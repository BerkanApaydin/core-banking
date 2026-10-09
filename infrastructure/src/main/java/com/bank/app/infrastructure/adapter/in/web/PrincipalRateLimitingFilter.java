package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * M-3: principal-keyed second bucket for authenticated resource prefixes.
 *
 * <p>
 * {@code RateLimitingFilter} (pre-auth, IP-keyed) owns only
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
public class PrincipalRateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(PrincipalRateLimitingFilter.class);

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public PrincipalRateLimitingFilter(RateLimiter rateLimiter,
            MessageSource messageSource,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = RequestPathResolver.resolve(httpRequest);
        String matchedPrefix = null;
        for (String prefix : rateLimitProperties.paths()) {
            if (!rateLimitProperties.isAuthTier(prefix)
                    && RequestPathResolver.matchesPrefix(path, prefix)) {
                matchedPrefix = prefix;
                break;
            }
        }
        if (matchedPrefix == null) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();
        boolean isWriteOperation = "POST".equals(method) || "PUT".equals(method)
                || "DELETE".equals(method) || "PATCH".equals(method);
        boolean isProtectedRead = "GET".equals(method) || "HEAD".equals(method);
        if (!isWriteOperation && !isProtectedRead) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalKey = principalKey(authentication);
        if (principalKey == null) {
            // No authenticated principal (public path or already-rejected
            // request): nothing principal-keyed to enforce. Pass through —
            // the pre-auth IP filter and the security chain own this case.
            chain.doFilter(request, response);
            return;
        }

        final int tierMaxRequests = rateLimitProperties.maxRequestsFor(matchedPrefix);
        final long tierWindowMs = rateLimitProperties.timeWindowMsFor(matchedPrefix);
        final String bucketKey = "principal:" + principalKey + "|" + matchedPrefix;
        final boolean acquired;
        try {
            acquired = rateLimiter.tryAcquire(bucketKey, tierMaxRequests, tierWindowMs);
        } catch (DataAccessException ex) {
            log.warn("Principal rate limiting backend unavailable: {}",
                    ex.getClass().getSimpleName());
            String message = messageSource.getMessage("error.security_backend_unavailable", null,
                    "Security service temporarily unavailable. Please try again later.",
                    LocaleContextHolder.getLocale());
            ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                    "SECURITY_BACKEND_UNAVAILABLE", message, path);
            return;
        }
        if (!acquired) {
            String message = messageSource.getMessage("error.rate_limit_exceeded", null,
                    "Too many requests. Please try again later.", LocaleContextHolder.getLocale());
            long windowSeconds = Math.max(1, tierWindowMs / 1000);
            httpResponse.setHeader("Retry-After", String.valueOf(windowSeconds));
            httpResponse.setHeader("RateLimit-Limit", String.valueOf(tierMaxRequests));
            httpResponse.setHeader("RateLimit-Remaining", "0");
            httpResponse.setHeader("RateLimit-Reset", String.valueOf(windowSeconds));
            ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", message, path);
            return;
        }

        httpResponse.setHeader("RateLimit-Limit", String.valueOf(tierMaxRequests));
        httpResponse.setHeader("RateLimit-Reset", String.valueOf(Math.max(1, tierWindowMs / 1000)));
        chain.doFilter(request, response);
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
