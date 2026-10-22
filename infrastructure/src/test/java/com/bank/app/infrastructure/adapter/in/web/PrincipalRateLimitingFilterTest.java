package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-3: principal-keyed bucket for authenticated resource prefixes.
 * One NAT address, two users — budgets must be independent.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class PrincipalRateLimitingFilterTest {

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private MessageSource messageSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitProperties props =
            new RateLimitProperties(null, null, 10, 10_000, 120, 60_000, 10000);

    private PrincipalRateLimitingFilter filter() {
        return new PrincipalRateLimitingFilter(rateLimiter, messageSource, props, objectMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static MockHttpServletRequest resourceRequest(String uri, String method, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setMethod(method);
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void backendLabelIdentifiesPrincipalLimiter() {
        assertEquals("Principal rate limiting", filter().backendLabel());
    }

    @Test
    void anonymousResourcePassesThrough() throws Exception {
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void authenticatedUnderLimitPassesThrough() throws Exception {
        authenticate("alice");
        when(rateLimiter.tryAcquire("principal:name:alice|/api/v1/transfers", 120, 60_000L))
                .thenReturn(true);
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("120", response.getHeader("RateLimit-Limit"));
        assertEquals("60", response.getHeader("RateLimit-Reset"));
    }

    @Test
    void authenticatedOverLimitGets429() throws Exception {
        authenticate("alice");
        when(rateLimiter.tryAcquire("principal:name:alice|/api/v1/accounts", 120, 60_000L))
                .thenReturn(false);
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests. Please try again later.");
        MockHttpServletRequest request = resourceRequest("/api/v1/accounts", "POST", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertEquals("120", response.getHeader("RateLimit-Limit"));
        assertEquals("0", response.getHeader("RateLimit-Remaining"));
        assertEquals("60", response.getHeader("RateLimit-Reset"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void sameNatDifferentUsersHaveIndependentBudgets() throws Exception {
        // Same egress IP, two identities: exhausting alice must not starve bob.
        when(rateLimiter.tryAcquire("principal:name:alice|/api/v1/transfers", 120, 60_000L))
                .thenReturn(false);
        when(rateLimiter.tryAcquire("principal:name:bob|/api/v1/transfers", 120, 60_000L))
                .thenReturn(true);
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("Too many requests.");

        authenticate("alice");
        MockHttpServletResponse aliceResp = new MockHttpServletResponse();
        filter().doFilter(resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.9"),
                aliceResp, mock(FilterChain.class));
        assertEquals(429, aliceResp.getStatus());

        authenticate("bob");
        MockHttpServletResponse bobResp = new MockHttpServletResponse();
        FilterChain bobChain = mock(FilterChain.class);
        filter().doFilter(resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.9"),
                bobResp, bobChain);
        assertEquals(200, bobResp.getStatus());
        verify(bobChain).doFilter(any(), any());
    }

    @Test
    void authTierIsNotHandledHere() throws Exception {
        // /api/v1/auth/* belongs to the pre-auth IP filter; this filter must
        // not double-count it even for authenticated callers.
        authenticate("alice");
        MockHttpServletRequest request = resourceRequest("/api/v1/auth/login", "POST", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void percentEncodedAliasUsesDecodedPrefix() throws Exception {
        // %61 == 'a': decoded to /api/v1/accounts, must hit the accounts
        // bucket — not bypass it.
        authenticate("alice");
        when(rateLimiter.tryAcquire("principal:name:alice|/api/v1/accounts", 120, 60_000L))
                .thenReturn(true);
        MockHttpServletRequest request = resourceRequest("/api/v1/%61ccounts", "POST", "10.0.0.77");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, times(1))
                .tryAcquire("principal:name:alice|/api/v1/accounts", 120, 60_000L);
    }

    @Test
    void unsupportedMethodPassesThroughWithoutRateLimit() throws Exception {
        authenticate("alice");
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "OPTIONS", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void unauthenticatedRequestPassesThroughWithoutPrincipalBucket() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void anonymousUserPassesThroughWithoutPrincipalBucket() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void blankPrincipalNameGetsNoRateLimitBucket() throws Exception {
        // A blank identity cannot be keyed per principal. It must not become
        // one shared global bucket (which one client could exhaust for
        // everyone) — the request passes through instead.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  ", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void nullPrincipalNameGetsNoRateLimitBucket() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(null, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpServletRequest request = resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyLong());
    }

    @Test
    void userIdPrincipalUsesIdBucket() throws Exception {
        AuthenticatedPrincipalPort port =
                mock(AuthenticatedPrincipalPort.class);
        when(port.getAuthenticatedUserId()).thenReturn(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(port, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(rateLimiter.tryAcquire("principal:42|/api/v1/transfers", 120, 60_000L)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1"), response, chain);

        verify(chain).doFilter(any(), any());
        verify(rateLimiter).tryAcquire("principal:42|/api/v1/transfers", 120, 60_000L);
    }

    @Test
    void usernamePrincipalUsesNameBucket() throws Exception {
        AuthenticatedPrincipalPort port =
                mock(AuthenticatedPrincipalPort.class);
        when(port.getAuthenticatedUserId()).thenReturn(null);
        when(port.getAuthenticatedUsername()).thenReturn("carol");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(port, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(rateLimiter.tryAcquire("principal:name:carol|/api/v1/transfers", 120, 60_000L))
                .thenReturn(true);
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(resourceRequest("/api/v1/transfers/123", "GET", "10.0.0.1"),
                new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void resourceTierRetryAfterComesFromResourceWindow() throws Exception {
        RateLimitProperties tight = new RateLimitProperties(null, null, 10, 10_000, 2, 1_000, 10000);
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(tight);
        PrincipalRateLimitingFilter tightFilter =
                new PrincipalRateLimitingFilter(limiter, messageSource, tight, objectMapper);
        authenticate("carol");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        for (int i = 0; i < 3; i++) {
            resp = new MockHttpServletResponse();
            tightFilter.doFilter(resourceRequest("/api/v1/accounts", "POST", "10.0.0.101"),
                    resp, mock(FilterChain.class));
        }

        assertEquals(429, resp.getStatus());
        assertEquals("1", resp.getHeader("Retry-After"));
    }
}
