package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backend-failure and client-keying behaviour of the pre-auth rate limiter.
 *
 * <p>
 * The limiter must never fail OPEN (a broken backend must not become an
 * unlimited budget) and a client without a resolvable IP must not silently
 * share an unlimited budget with nobody.
 */
@SuppressWarnings({ "null", "unchecked" })
@ExtendWith(MockitoExtension.class)
class RateLimitingFilterBackendFailureTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private MessageSource messageSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitProperties props = new RateLimitProperties(null, null, 10, 10_000, 120, 60_000, 10000);
    private final TrustedProxyClientIpResolver clientIpResolver = new TrustedProxyClientIpResolver(new ProxyProperties(true));

    @BeforeEach
    void setUp() {
        // Stubbed per test: only the rejection paths ask for a message.
    }

    private void stubMessages() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Security service temporarily unavailable. Please try again later.");
    }

    private RateLimitingFilter redisBackedFilter() {
        return new RateLimitingFilter(new RedisRateLimiter(redisTemplate, props),
                messageSource, props, objectMapper, clientIpResolver);
    }

    private static MockHttpServletRequest loginRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.setMethod("POST");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    @DisplayName("a Redis outage becomes a fail-closed 503, never an allow")
    void shouldTranslateADataAccessFailureIntoA503() throws Exception {
        stubMessages();
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(),
                anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        redisBackedFilter().doFilter(loginRequest("10.0.0.1"), response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("SECURITY_BACKEND_UNAVAILABLE"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("a non-DataAccess backend failure also fails closed with 503")
    void shouldTranslateAnyBackendRuntimeFailureIntoA503() throws Exception {
        // A not-yet-initialised Redis client throws IllegalStateException, not
        // DataAccessException; letting it escape produced an unhandled 500.
        stubMessages();
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(),
                anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("lettuce client is not initialised"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        redisBackedFilter().doFilter(loginRequest("10.0.0.1"), response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("SECURITY_BACKEND_UNAVAILABLE"));
        verify(chain, never()).doFilter(any(), any());
    }
}
