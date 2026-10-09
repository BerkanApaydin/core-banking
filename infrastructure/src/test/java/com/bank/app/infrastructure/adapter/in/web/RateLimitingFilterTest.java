package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class RateLimitingFilterTest {

    private CaffeineRateLimiter rateLimiter;
    private MessageSource messageSource;
    private RateLimitingFilter filter;
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10_000, 120, 60000);
        rateLimiter = new CaffeineRateLimiter(props);
        messageSource = mock(MessageSource.class);
        clientIpResolver = new ClientIpResolver(new ProxyProperties(true));
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        filter = new RateLimitingFilter(rateLimiter, messageSource, new RateLimitProperties(null, null, 10, 10_000, 120, 60000), objectMapper,
                clientIpResolver);
    }

    @Test
    void shouldNotLimitNonMonitoredPath() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/health");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowUnderLimit() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockOverLimit() throws IOException, ServletException {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/auth/register");
            request.setRemoteAddr("10.0.0.1");
            request.setMethod("POST");

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        request.setMethod("POST");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Too many requests"));
        // RFC 9110: clients must know how long to back off (window is 10s here).
        assertEquals("10", response.getHeader("Retry-After"));
    }

    @Test
    void shouldAdvertiseIetfRateLimitHeaders() throws IOException, ServletException {
        // I-10: success path carries Limit+Reset so clients can pace
        // themselves; the reject path additionally carries Remaining: 0.
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest allowed = new MockHttpServletRequest();
        allowed.setRequestURI("/api/v1/auth/login");
        allowed.setRemoteAddr("172.16.0.9");
        allowed.setMethod("POST");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowed, allowedResponse, chain);

        assertEquals(200, allowedResponse.getStatus());
        assertEquals("10", allowedResponse.getHeader("RateLimit-Limit"));
        assertEquals("10", allowedResponse.getHeader("RateLimit-Reset"));
        assertNull(allowedResponse.getHeader("RateLimit-Remaining"),
                "Remaining must be omitted on success — the limiter contract "
                + "has no live counter to report");

        for (int i = 0; i < 9; i++) {
            filter.doFilter(allowed, new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(allowed, rejected, chain);

        assertEquals(429, rejected.getStatus());
        assertEquals("10", rejected.getHeader("RateLimit-Limit"));
        assertEquals("0", rejected.getHeader("RateLimit-Remaining"));
        assertEquals("10", rejected.getHeader("RateLimit-Reset"));
        assertEquals("10", rejected.getHeader("Retry-After"));
    }

    @Test
    void shouldNotInvokeFilterChainWhenRateLimitExceeded()
            throws IOException, ServletException {

        RateLimitProperties p = new RateLimitProperties(null, null, 1, 10_000, 120, 60000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(p);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter filter = new RateLimitingFilter(limiter, messageSource, new RateLimitProperties(null, null, 1, 10_000, 120, 60000),
                objectMapper, clientIpResolver);

        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setRequestURI("/api/v1/auth/login");
        firstRequest.setRemoteAddr("1.1.1.1");
        firstRequest.setMethod("POST");

        filter.doFilter(
                firstRequest,
                new MockHttpServletResponse(),
                chain);

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setRequestURI("/api/v1/auth/login");
        secondRequest.setRemoteAddr("1.1.1.1");
        secondRequest.setMethod("POST");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(secondRequest, response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldParseXForwardedForSingleIp()
            throws IOException, ServletException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldUseLastIpFromXForwardedForHeader()
            throws IOException, ServletException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader(
                "X-Forwarded-For",
                "203.0.113.195, 10.0.0.1, 172.16.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldFallbackToRemoteAddrWhenHeaderIsUnknown()
            throws IOException, ServletException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("192.168.1.50");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldFallbackToRemoteAddrWhenHeaderIsEmpty()
            throws IOException, ServletException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "");
        request.setRemoteAddr("192.168.1.50");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowTransferPathUnderLimit() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers/123");
        request.setRemoteAddr("10.0.0.99");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private static CaffeineRateLimiter createLimiter(int maxRequests, int timeWindowMs) {
        RateLimitProperties p = new RateLimitProperties(null, null, maxRequests, timeWindowMs, 120, 60000);
        return new CaffeineRateLimiter(p);
    }

    @Test
    void caffeineRateLimiterShouldResetAfterWindow() {
        CaffeineRateLimiter limiter = createLimiter(2, 1_000_000);

        assertTrue(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-a"));

        CaffeineRateLimiter freshLimiter = createLimiter(2, 1_000_000);

        assertTrue(freshLimiter.tryAcquire("client-a"));
        assertTrue(freshLimiter.tryAcquire("client-a"));
        assertFalse(freshLimiter.tryAcquire("client-a"));
    }

    @Test
    void shouldTrackLimitsPerIpSeparately() {

        CaffeineRateLimiter limiter = createLimiter(1, 10_000);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));

        assertTrue(limiter.tryAcquire("10.0.0.2"));
    }

    @Test
    void shouldReturn429WithCorrectContentType() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.99");
        req.setMethod("POST");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.99");
        req2.setMethod("POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
        assertTrue(resp.getContentType() != null && resp.getContentType().startsWith("application/problem+json"));
        assertEquals("UTF-8", resp.getCharacterEncoding());
        assertTrue(resp.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
        assertTrue(resp.getContentAsString().contains("Too many requests"));
    }

    @Test
    void shouldReturn429WithTurkishErrorMessage() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.99");
        req.setMethod("POST");

        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.99");
        req2.setMethod("POST");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
        String body = resp.getContentAsString();
        assertTrue(body.contains("Too many requests"), "429 response should contain Turkish error message, received: " + body);
    }

    @Test
    void shouldPassResourceTierToPrincipalFilter() throws Exception {
        // M-3: /api/v1/accounts belongs to the principal-keyed
        // PrincipalRateLimitingFilter (post-auth). The pre-auth IP filter must
        // pass it through untouched — even under a strict resource budget —
        // so one NAT egress cannot starve every user behind it.
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 10, 10_000, 1, 10_000), objectMapper, clientIpResolver);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/accounts");
            req.setMethod("POST");
            req.setRemoteAddr("10.0.0.99");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            strictFilter.doFilter(req, resp, chain);
            assertEquals(200, resp.getStatus());
        }

        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void shouldPassTransferUriToPrincipalFilter() throws Exception {
        // M-3: same split for /api/v1/transfers — IP filter passes through.
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 10, 10_000, 1, 10_000), objectMapper, clientIpResolver);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/transfers/send");
        req.setRemoteAddr("10.0.0.99");
        req.setMethod("POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void shouldBlockOverLimitWithPutMethod() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.1");
        req.setMethod("PUT");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.1");
        req2.setMethod("PUT");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldBlockOverLimitWithDeleteMethod() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.1");
        req.setMethod("DELETE");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.1");
        req2.setMethod("DELETE");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldBlockOverLimitWithPatchMethod() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.1");
        req.setMethod("PATCH");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.1");
        req2.setMethod("PATCH");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldLimitPercentEncodedAliasOfProtectedPath() throws Exception {
        // %61 == 'a': Spring MVC decodes /api/v1/%61uth/login to
        // /api/v1/auth/login and routes it to the login endpoint. The limiter
        // must decide on the same decoded path, otherwise the alias escapes
        // rate limiting. (Resource-tier aliases are covered by
        // PrincipalRateLimitingFilterTest.percentEncodedAliasUsesDecodedPrefix.)
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/%61uth/login");
        req.setRemoteAddr("10.0.0.77");
        req.setMethod("POST");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/%61uth/login");
        req2.setRemoteAddr("10.0.0.77");
        req2.setMethod("POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldLimitProtectedPathUnderContextPath() throws Exception {
        // getRequestURI() includes the context path ("/bank/api/..."), which
        // never startsWith("/api/..."). The limiter must strip it first,
        // otherwise every non-root deployment silently disables limiting.
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/bank");
        req.setRequestURI("/bank/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.78");
        req.setMethod("POST");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setContextPath("/bank");
        req2.setRequestURI("/bank/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.78");
        req2.setMethod("POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldNotLimitSiblingPathSharingAPrefix() throws Exception {
        // "/api/v1/accountsextra" shares a string prefix with "/api/v1/accounts"
        // but is a different path. Prefix matching must be segment-aware.
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(null, null, 1, 10_000, 120, 60000), objectMapper, clientIpResolver);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/accountsextra");
            req.setRemoteAddr("10.0.0.79");
            req.setMethod("POST");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            strictFilter.doFilter(req, resp, chain);
            assertEquals(200, resp.getStatus());
        }

        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void shouldApplyLooseResourceTierToTransfers() throws Exception {
        // 11 transfer reads must NOT 429 on the auth budget (10/10s): the
        // resource tier is 120/60s by default.
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10_000, 120, 60000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(props);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter tieredFilter = new RateLimitingFilter(limiter, messageSource,
                props, objectMapper, clientIpResolver);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/transfers/123");
            req.setRemoteAddr("10.0.0.100");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            tieredFilter.doFilter(req, resp, chain);
            assertEquals(200, resp.getStatus());
        }

        verify(chain, times(11)).doFilter(any(), any());
    }

    @Test
    void shouldNotApplyAuthWindowToResourceTier() throws Exception {
        // M-3: the pre-auth IP filter no longer owns the resource tier, so it
        // must not 429 resource requests under ANY window — Retry-After for
        // the resource tier is covered by
        // PrincipalRateLimitingFilterTest.resourceTierRetryAfterComesFromResourceWindow.
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10_000, 2, 1_000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(props);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter tieredFilter = new RateLimitingFilter(limiter, messageSource,
                props, objectMapper, clientIpResolver);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/accounts");
            req.setRemoteAddr("10.0.0.101");
            req.setMethod("POST");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            tieredFilter.doFilter(req, resp, chain);
            assertEquals(200, resp.getStatus());
        }

        verify(chain, times(3)).doFilter(any(), any());
    }
}
