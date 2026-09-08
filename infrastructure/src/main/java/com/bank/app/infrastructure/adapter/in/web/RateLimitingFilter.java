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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final List<String> rateLimitedPaths;
    private final long retryAfterSeconds;
    private final ObjectMapper objectMapper;
    private final ClientIpResolverPort clientIpResolver;

    public RateLimitingFilter(RateLimiter rateLimiter,
                              MessageSource messageSource,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper,
                              ClientIpResolverPort clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
        this.rateLimitedPaths = rateLimitProperties.getPaths();
        // RFC 9110 Retry-After for 429 responses, derived from the same window
        // the limiter enforces so clients back off exactly long enough.
        this.retryAfterSeconds = Math.max(1, rateLimitProperties.getTimeWindowMs() / 1000);
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();

        boolean matchesRateLimitedPath = rateLimitedPaths.stream().anyMatch(path::startsWith);
        if (!matchesRateLimitedPath) {
            chain.doFilter(request, response);
            return;
        }

        boolean isWriteOperation = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
        // Single-resource GETs (by id/IBAN) enable enumeration if unlimited;
        // list/report/history reads are expensive. Limit all GETs under the
        // protected prefixes, not just report/history.
        boolean isProtectedRead = "GET".equals(method);

        if (!isWriteOperation && !isProtectedRead) {
            chain.doFilter(request, response);
            return;
        }
        String ip = clientIpResolver.resolveClientIp(
                httpRequest.getHeader("X-Forwarded-For"), httpRequest.getRemoteAddr());

            if (!rateLimiter.tryAcquire(ip)) {
                String message = messageSource.getMessage("error.rate_limit_exceeded", null,
                        "Too many requests. Please try again later.", LocaleContextHolder.getLocale());
                // Literal code (equals ErrorCode.RATE_LIMIT_EXCEEDED.code()): the web layer must
                // not depend on the domain.exception package (see ArchitectureTest).
                httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED", message, path);
                return;
            }

        chain.doFilter(request, response);
    }

}
