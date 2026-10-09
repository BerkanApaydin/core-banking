package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolverPort clientIpResolver;

    public RateLimitingFilter(RateLimiter rateLimiter,
                              MessageSource messageSource,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper,
                              ClientIpResolverPort clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        // Decoded, context-path-free path: the same string Spring MVC matches
        // handlers against, so percent-encoded aliases (e.g. /api/v1/%61ccounts)
        // and non-root deployments cannot slip past the prefix list.
        String path = RequestPathResolver.resolve(httpRequest);

        String matchedPrefix = null;
        for (String prefix : rateLimitProperties.paths()) {
            if (RequestPathResolver.matchesPrefix(path, prefix)) {
                matchedPrefix = prefix;
                break;
            }
        }
        if (matchedPrefix == null) {
            chain.doFilter(request, response);
            return;
        }
        // M-3: this pre-authentication filter owns ONLY the auth tier
        // (/api/v1/auth/*). It runs before authentication
        // (HIGHEST_PRECEDENCE + 1) so no principal exists yet — IP-keying is
        // correct here (brute-force protection needs no identity). Resource
        // prefixes (/accounts, /transfers, /admin) are owned by
        // PrincipalRateLimitingFilter, which keys by authenticated principal:
        // IP-keying them lets one NAT/botnet client starve every legitimate
        // user behind the same egress, and gives each bot IP an independent
        // budget on authenticated endpoints.
        if (!rateLimitProperties.isAuthTier(matchedPrefix)) {
            chain.doFilter(request, response);
            return;
        }

        boolean isWriteOperation = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
        // Single-resource GETs (by id/IBAN) enable enumeration if unlimited;
        // list/report/history reads are expensive. Limit all GETs under the
        // protected prefixes, not just report/history. HEAD can fetch the same
        // metadata without a body, so it shares the read budget; OPTIONS stays
        // unlimited for CORS preflight.
        boolean isProtectedRead = "GET".equals(method) || "HEAD".equals(method);

        if (!isWriteOperation && !isProtectedRead) {
            chain.doFilter(request, response);
            return;
        }
        String ip = clientIpResolver.resolveClientIp(
                httpRequest.getHeader("X-Forwarded-For"), httpRequest.getRemoteAddr());

        // Per-endpoint bucket per client ("ip|prefix"): login abuse must not
        // eat the refresh budget and vice versa. "|" is unambiguous here (it
        // appears in neither IPs nor matched prefixes).
        //
        // Auth tier only (see above): buckets stay IP-keyed on purpose — this
        // filter runs before authentication (HIGHEST_PRECEDENCE + 1), so no
        // principal exists yet, and the auth endpoints that need protection
        // most have no principal by definition. Spoofing is contained by
        // trust-forwarded-headers=false unless a trusted proxy overwrites
        // X-Forwarded-For.
        final int tierMaxRequests = rateLimitProperties.maxRequestsFor(matchedPrefix);
        final long tierWindowMs = rateLimitProperties.timeWindowMsFor(matchedPrefix);
        final String bucketKey = ip + "|" + matchedPrefix;
        final boolean acquired;
        try {
            acquired = rateLimiter.tryAcquire(bucketKey, tierMaxRequests, tierWindowMs);
        } catch (DataAccessException ex) {
            // A Redis-backed limiter cannot make a trustworthy allow/deny decision.
            // Filters run before MVC exception advice, so write the response here.
            log.warn("Rate limiting backend unavailable: {}", ex.getClass().getSimpleName());
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
            // Literal code (equals ErrorCode.RATE_LIMIT_EXCEEDED.code()): the web layer must
            // not depend on the domain.exception package (see ArchitectureTest).
            // RFC 9110 Retry-After, derived from the same tier window the
            // limiter enforces so clients back off exactly long enough.
            long windowSeconds = Math.max(1, tierWindowMs / 1000);
            httpResponse.setHeader("Retry-After", String.valueOf(windowSeconds));
            // IETF RateLimit header fields (draft-ietf-httpapi-ratelimit-headers):
            // Limit/Reset on every decision, Remaining only on reject (the
            // limiter contract returns allow/deny without a live counter, so a
            // success-path Remaining would be fabricated — report 0 only when
            // the bucket is provably exhausted).
            httpResponse.setHeader("RateLimit-Limit", String.valueOf(tierMaxRequests));
            httpResponse.setHeader("RateLimit-Remaining", "0");
            httpResponse.setHeader("RateLimit-Reset", String.valueOf(windowSeconds));
            ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", message, path);
            return;
        }

        // I-10: advertise the enforced budget on the success path too, so
        // well-behaved clients can pace themselves before hitting 429.
        httpResponse.setHeader("RateLimit-Limit", String.valueOf(tierMaxRequests));
        httpResponse.setHeader("RateLimit-Reset", String.valueOf(Math.max(1, tierWindowMs / 1000)));
        chain.doFilter(request, response);
    }

}
