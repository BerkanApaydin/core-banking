package com.bank.app.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    @Test
    void shouldNotLimitNonMonitoredPath() throws IOException, ServletException {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
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
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
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
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/auth/register");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }

        // 11th request
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Çok fazla istek"));
    }

    @Test
    void shouldParseXForwardedForSingleIp() throws IOException, ServletException {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-Forwarded-For", "203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldParseXForwardedForMultipleIps() throws IOException, ServletException {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldParseXForwardedForUnknown() throws IOException, ServletException {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldParseXForwardedForEmpty() throws IOException, ServletException {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-Forwarded-For", "");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResetLimitWhenExpired() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(10, 10000);
        
        Class<?> infoClass = Class.forName("com.bank.app.common.web.RateLimitingFilter$RateLimitInfo");
        java.lang.reflect.Constructor<?> constructor = infoClass.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);
        Object expiredInfo = constructor.newInstance(5, -10000L); // count = 5, expired 10 seconds ago

        com.github.benmanes.caffeine.cache.Cache<String, Object> cache = 
                (com.github.benmanes.caffeine.cache.Cache<String, Object>) ReflectionTestUtils.getField(filter, "cache");
        assertNotNull(cache);
        cache.asMap().put("192.168.1.1", expiredInfo);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());

        // Verify that the entry was replaced with a new one starting with count 1
        Object currentInfo = cache.asMap().get("192.168.1.1");
        assertNotNull(currentInfo);
        assertNotSame(expiredInfo, currentInfo);
        
        java.lang.reflect.Field countField = infoClass.getDeclaredField("requestCount");
        countField.setAccessible(true);
        java.util.concurrent.atomic.AtomicInteger count = (java.util.concurrent.atomic.AtomicInteger) countField.get(currentInfo);
        assertEquals(1, count.get());
    }

    @Test
    void testRateLimitInfoExpiry() throws Exception {
        Class<?> infoClass = Class.forName("com.bank.app.common.web.RateLimitingFilter$RateLimitInfo");
        java.lang.reflect.Constructor<?> constructor = infoClass.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);
        Object infoInstance = constructor.newInstance(1, -10000L); // duration is -10 seconds (expired)
        
        java.lang.reflect.Method isExpiredMethod = infoClass.getDeclaredMethod("isExpired");
        isExpiredMethod.setAccessible(true);
        boolean isExpired = (boolean) isExpiredMethod.invoke(infoInstance);
        assertTrue(isExpired);

        Object infoInstance2 = constructor.newInstance(1, 10000L); // duration is +10 seconds (not expired)
        boolean isExpired2 = (boolean) isExpiredMethod.invoke(infoInstance2);
        assertFalse(isExpired2);
    }
}
