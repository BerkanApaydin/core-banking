package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.messages.MessageKeys;
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
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * Shared allow/deny handling for the two rate-limit tiers.
 *
 * <p>
 * Subclasses only decide which prefixes they own and how a request maps to a
 * bucket key. Method filtering, backend-failure handling (503), rejection
 * headers (Retry-After, RateLimit-*), and success-path budget advertisement
 * live here so both tiers cannot drift apart.
 */
abstract class AbstractRateLimitingFilter implements Filter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    AbstractRateLimitingFilter(RateLimiter rateLimiter,
            MessageSource messageSource,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    protected final RateLimitProperties rateLimitProperties() {
        return rateLimitProperties;
    }

    protected abstract boolean ownsPrefix(String prefix);

    protected abstract String backendLabel();

    /**
     * Returns the bucket key for the request, or {@code null} when the
     * subclass cannot key it and the request must pass through.
     */
    protected abstract String bucketKey(HttpServletRequest request, String matchedPrefix);

    @Override
    public final void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        String path = RequestPathResolver.resolve(httpRequest);
        String matchedPrefix = null;
        for (String prefix : rateLimitProperties.paths()) {
            if (ownsPrefix(prefix) && RequestPathResolver.matchesPrefix(path, prefix)) {
                matchedPrefix = prefix;
                break;
            }
        }
        if (matchedPrefix == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!isLimitedMethod(method)) {
            chain.doFilter(request, response);
            return;
        }

        String bucketKey = bucketKey(httpRequest, matchedPrefix);
        if (bucketKey == null) {
            chain.doFilter(request, response);
            return;
        }

        final int tierMaxRequests = rateLimitProperties.maxRequestsFor(matchedPrefix);
        final long tierWindowMs = rateLimitProperties.timeWindowMsFor(matchedPrefix);
        final boolean acquired;
        try {
            acquired = rateLimiter.tryAcquire(bucketKey, tierMaxRequests, tierWindowMs);
        } catch (RuntimeException ex) {
            // Any backend outage must fail CLOSED with a retryable 503, not
            // leak as an unhandled 500: a Redis DataAccessException is the
            // common case, but a not-yet-initialised client throws
            // IllegalStateException and an AOP/proxy hiccup can throw others.
            // The stack trace keeps genuine programming errors visible.
            log.warn("{} backend unavailable: {}", backendLabel(), ex.getClass().getSimpleName(), ex);
            String message = messageSource.getMessage(MessageKeys.SECURITY_BACKEND_UNAVAILABLE, null,
                    "Security service temporarily unavailable. Please try again later.",
                    LocaleContextHolder.getLocale());
            ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                    "SECURITY_BACKEND_UNAVAILABLE", message, path);
            return;
        }
        if (!acquired) {
            String message = messageSource.getMessage(MessageKeys.RATE_LIMIT_EXCEEDED, null,
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

    private static boolean isLimitedMethod(String method) {
        // Single-resource GETs (by id/IBAN) enable enumeration if unlimited;
        // list/report/history reads are expensive. Limit all GETs under the
        // protected prefixes, not just report/history. HEAD can fetch the same
        // metadata without a body, so it shares the read budget; OPTIONS stays
        // unlimited for CORS preflight.
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)
                || "PATCH".equals(method) || "GET".equals(method) || "HEAD".equals(method);
    }
}
