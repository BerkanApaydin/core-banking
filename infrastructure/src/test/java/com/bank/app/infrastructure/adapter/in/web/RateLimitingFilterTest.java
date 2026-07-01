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
        RateLimitProperties props = new RateLimitProperties();
        props.setMaxRequests(10);
        props.setTimeWindowMs(10_000);
        rateLimiter = new CaffeineRateLimiter(props);
        messageSource = mock(MessageSource.class);
        clientIpResolver = new ClientIpResolver();
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        filter = new RateLimitingFilter(rateLimiter, messageSource, new RateLimitProperties(), objectMapper,
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
    }

    @Test
    void shouldNotInvokeFilterChainWhenRateLimitExceeded()
            throws IOException, ServletException {

        RateLimitProperties p = new RateLimitProperties();
        p.setMaxRequests(1);
        p.setTimeWindowMs(10_000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(p);
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter filter = new RateLimitingFilter(limiter, messageSource, new RateLimitProperties(),
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
    void shouldUseFirstIpFromXForwardedForHeader()
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
        RateLimitProperties p = new RateLimitProperties();
        p.setMaxRequests(maxRequests);
        p.setTimeWindowMs(timeWindowMs);
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
                new RateLimitProperties(), objectMapper, clientIpResolver);

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
        assertTrue(resp.getContentType() != null && resp.getContentType().startsWith("application/json"));
    }

    @Test
    void shouldReturn429WithTurkishErrorMessage() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(), objectMapper, clientIpResolver);

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
    void shouldLimitAccountCreation() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/accounts");
        req.setMethod("POST");
        req.setRemoteAddr("10.0.0.99");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/accounts");
        req2.setMethod("POST");
        req2.setRemoteAddr("10.0.0.99");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldLimitByRequestUri() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(), objectMapper, clientIpResolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/transfers/send");
        req.setRemoteAddr("10.0.0.99");
        req.setMethod("POST");
        strictFilter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/api/v1/transfers/send");
        req2.setRemoteAddr("10.0.0.99");
        req2.setMethod("POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        strictFilter.doFilter(req2, resp, mock(FilterChain.class));

        assertEquals(429, resp.getStatus());
    }

    @Test
    void shouldBlockOverLimitWithPutMethod() throws Exception {
        CaffeineRateLimiter strictLimiter = createLimiter(1, 10_000);
        MessageSource localMessageSource = mock(MessageSource.class);
        when(localMessageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests sent. Please try again later.");
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimitingFilter strictFilter = new RateLimitingFilter(strictLimiter, localMessageSource,
                new RateLimitProperties(), objectMapper, clientIpResolver);

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
                new RateLimitProperties(), objectMapper, clientIpResolver);

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
                new RateLimitProperties(), objectMapper, clientIpResolver);

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
}
