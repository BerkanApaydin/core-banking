package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider JwtTokenProvider;

    @Mock
    private TokenBlacklistPort tokenBlacklistPort;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(JwtTokenProvider, tokenBlacklistPort, new ObjectMapper());
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void shouldContinueChainWhenAuthHeaderIsNull() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(JwtTokenProvider, tokenBlacklistPort);
    }

    @Test
    void shouldContinueChainWhenAuthHeaderDoesNotStartWithBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic userpass");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(JwtTokenProvider, tokenBlacklistPort);
    }

    @Test
    void shouldSend401WhenJwtServiceThrowsException() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalidjwt");
        when(JwtTokenProvider.extractUsername("invalidjwt")).thenThrow(new RuntimeException("invalid token"));

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueChainWhenUsernameIsNull() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);
        when(tokenBlacklistPort.isBlacklisted("token")).thenReturn(false);

        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSkipAuthenticationWhenTokenIsInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(false);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.extractUserId("token")).thenReturn(42L);
        when(JwtTokenProvider.extractRole("token")).thenReturn("ROLE_USER");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        assertNotNull(auth.getDetails());
    }

    @Test
    void shouldExposeUserIdInMdcDuringChainAndRemoveItAfterwards() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.extractUserId("token")).thenReturn(42L);
        when(JwtTokenProvider.extractRole("token")).thenReturn("ROLE_USER");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);

        var mdcSeenInChain = new String[1];
        FilterChain capturingChain = (req, res) ->
                mdcSeenInChain[0] = org.slf4j.MDC.get("userId");

        filter.doFilterInternal(request, response, capturingChain);

        assertEquals("42", mdcSeenInChain[0]);
        // ThreadLocal must not leak to the next request on a reused thread.
        assertNull(org.slf4j.MDC.get("userId"));
    }

    @Test
    void shouldSend401OnBearerTokenWithMalformedJwt() throws Exception {
        // Blank token is rejected before touching the JWT port.
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should send 401 when token is blacklisted")
    void shouldSend401WhenTokenIsBlacklisted() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer blacklisted-token");
        when(JwtTokenProvider.extractUsername("blacklisted-token")).thenReturn("user");
        when(JwtTokenProvider.isTokenValid("blacklisted-token")).thenReturn(true);
        when(tokenBlacklistPort.isBlacklisted("blacklisted-token")).thenReturn(true);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        // Revoked and invalid tokens share one message (no revocation oracle).
        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should authenticate from JWT claims when userId and role are present")
    void shouldAuthenticateFromJwtClaims() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.extractUserId("token")).thenReturn(42L);
        when(JwtTokenProvider.extractRole("token")).thenReturn("ROLE_USER");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        assertEquals("user", auth.getName());
        assertEquals(1, auth.getAuthorities().size());
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("should reject legacy token without role claim instead of DB fallback")
    void shouldRejectLegacyTokenWithoutRoleClaim() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);
        when(JwtTokenProvider.extractUserId("token")).thenReturn(42L);
        when(JwtTokenProvider.extractRole("token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private static void assertProblemResponse(MockHttpServletResponse errorResponse, int status,
            String code, String message) throws Exception {
        assertEquals(status, errorResponse.getStatus());
        assertTrue(errorResponse.getContentType().contains("application/problem+json"));
        String body = errorResponse.getContentAsString();
        assertTrue(body.contains("\"code\":\"" + code + "\""), "expected code " + code + " in: " + body);
        assertTrue(body.contains(message), "expected message in: " + body);
    }
}
