package com.bank.app.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterEdgeCaseTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private MessageSource messageSource;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        when(request.getMethod()).thenReturn("POST");
        filter = new RateLimitingFilter(rateLimiter, messageSource, new RateLimitProperties());
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
        when(rateLimiter.tryAcquire("192.168.1.1")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockLoginRequestWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1")).thenReturn(false);
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
        assertTrue(stringWriter.toString().contains("Çok fazla istek gönderildi"));
    }

    @Test
    void shouldUseXForwardedForHeaderWhenPresent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsUnknown() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsEmpty() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.tryAcquire("192.168.1.1")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockTransferRequestWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(rateLimiter.tryAcquire("10.0.0.5")).thenReturn(false);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldBlockRegisterRequestWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(rateLimiter.tryAcquire("10.0.0.5")).thenReturn(false);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
    }
}
