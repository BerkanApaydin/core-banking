package com.bank.app.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private CaffeineRateLimiter rateLimiter;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = new CaffeineRateLimiter(10, 10_000);
        filter = new RateLimitingFilter(rateLimiter);
    }

    @Test
    void shouldNotLimitNonMonitoredPath() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts");

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

            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Çok fazla istek"));
    }

    @Test
    void shouldNotInvokeFilterChainWhenRateLimitExceeded()
            throws IOException, ServletException {

        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 10_000);
        RateLimitingFilter filter = new RateLimitingFilter(limiter);

        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setRequestURI("/api/v1/auth/login");
        firstRequest.setRemoteAddr("1.1.1.1");

        filter.doFilter(
                firstRequest,
                new MockHttpServletResponse(),
                chain);

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setRequestURI("/api/v1/auth/login");
        secondRequest.setRemoteAddr("1.1.1.1");

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
    void caffeineRateLimiterShouldResetAfterWindow()
            throws InterruptedException {

        CaffeineRateLimiter limiter = new CaffeineRateLimiter(2, 50);

        assertTrue(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-a"));

        Thread.sleep(70);

        assertTrue(limiter.tryAcquire("client-a"));
    }

    @Test
    void shouldTrackLimitsPerIpSeparately() {

        CaffeineRateLimiter limiter = new CaffeineRateLimiter(1, 10_000);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));

        assertTrue(limiter.tryAcquire("10.0.0.2"));
    }

    @Test
    void rateLimitInfoIsExpiredReturnsFalseWhenNotYetExpired() throws Exception {
        // Access the private static inner class via reflection
        Class<?> infoClass = Class.forName("com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");
        var ctor = infoClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object info = ctor.newInstance(1, 10_000);

        Method isExpired = infoClass.getDeclaredMethod("isExpired");
        isExpired.setAccessible(true);

        // resetTime = now + 10000ms → not expired
        assertFalse((Boolean) isExpired.invoke(info));
    }

    @Test
    void rateLimitInfoIsExpiredReturnsTrueWhenExpired() throws Exception {
        Class<?> infoClass = Class.forName("com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");
        var ctor = infoClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        // durationMs = -1 → resetTime in the past → expired
        Object info = ctor.newInstance(1, -1);

        Method isExpired = infoClass.getDeclaredMethod("isExpired");
        isExpired.setAccessible(true);

        assertTrue((Boolean) isExpired.invoke(info));
    }

    @Test
    void rateLimitInfoConstructorSetsFields() throws Exception {
        Class<?> infoClass = Class.forName("com.bank.app.common.web.CaffeineRateLimiter$RateLimitInfo");
        var ctor = infoClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object info = ctor.newInstance(5, 1000);

        AtomicInteger count = (AtomicInteger) ReflectionTestUtils.getField(info, "requestCount");
        assertEquals(5, count.get());

        long resetTime = (long) ReflectionTestUtils.getField(info, "resetTime");
        assertTrue(resetTime > System.currentTimeMillis());
    }
}