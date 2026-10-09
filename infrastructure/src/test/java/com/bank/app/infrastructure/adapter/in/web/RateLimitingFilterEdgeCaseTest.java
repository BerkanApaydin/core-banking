package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RateLimitingFilterEdgeCaseTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private MessageSource messageSource;

    private RateLimitingFilter filter;
    private ObjectMapper objectMapper;
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        when(request.getMethod()).thenReturn("POST");
        // Servlet contract: context path is never null ("" when absent).
        // RequestPathResolver relies on UrlPathHelper, which decodes it.
        when(request.getContextPath()).thenReturn("");
        objectMapper = new ObjectMapper();
        clientIpResolver = new ClientIpResolver(new ProxyProperties(true));
        filter = new RateLimitingFilter(rateLimiter, messageSource,
                new RateLimitProperties(null, null, 10, 10_000, 120, 60000), objectMapper, clientIpResolver);
    }

    @Test
    void shouldAllowRequestWhenNotRateLimitedPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/health");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void shouldAllowLoginRequestWhenUnderLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1|/api/v1/auth/login", 10, 10_000)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockLoginRequestWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1|/api/v1/auth/login", 10, 10_000)).thenReturn(false);
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Too many requests sent. Please try again later.");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
        assertTrue(stringWriter.toString().contains("Too many requests sent"));
    }

    @Test
    void shouldFailClosedWithProblemDetailWhenRedisLimiterIsUnavailable() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1|/api/v1/auth/login", 10, 10_000))
                .thenThrow(new RedisConnectionFailureException("Redis connection failed"));
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Security service temporarily unavailable.");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(503);
        verify(chain, never()).doFilter(request, response);
        assertTrue(body.toString().contains("SECURITY_BACKEND_UNAVAILABLE"));
        assertTrue(body.toString().contains("Security service temporarily unavailable."));
        assertFalse(body.toString().contains("Redis connection failed"));
    }

    @Test
    void shouldUseXForwardedForHeaderWhenPresent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        // Append-semantics: the edge proxy appends the peer it saw, so the
        // bucket uses the last (trustworthy) entry, not the spoofable first.
        when(rateLimiter.tryAcquire("10.0.0.2|/api/v1/auth/login", 10, 10_000)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsUnknown() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1|/api/v1/auth/login", 10, 10_000)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsEmpty() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1|/api/v1/auth/login", 10, 10_000)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassTransferRequestToPrincipalFilter() throws Exception {
        // M-3: resource prefixes belong to PrincipalRateLimitingFilter. The
        // pre-auth IP filter passes them through without touching the limiter.
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void shouldKeepPerEndpointBucketsOnAuthTier() throws Exception {
        // Per-endpoint buckets survive on the auth tier: register abuse must
        // not eat the login budget. Resource isolation moved to the
        // principal filter (PrincipalRateLimitingFilterTest).
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        when(rateLimiter.tryAcquire("10.0.0.9|/api/v1/auth/login", 10, 10_000)).thenReturn(true);
        when(rateLimiter.tryAcquire("10.0.0.9|/api/v1/auth/register", 10, 10_000)).thenReturn(true);

        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        filter.doFilter(request, response, chain);

        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        filter.doFilter(request, response, chain);

        verify(rateLimiter).tryAcquire("10.0.0.9|/api/v1/auth/login", 10, 10_000);
        verify(rateLimiter).tryAcquire("10.0.0.9|/api/v1/auth/register", 10, 10_000);
        verify(chain, times(2)).doFilter(request, response);
    }

    @Test
    void shouldBlockRegisterRequestWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(rateLimiter.tryAcquire("10.0.0.5|/api/v1/auth/register", 10, 10_000)).thenReturn(false);
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Rate limit exceeded");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
    }
}
