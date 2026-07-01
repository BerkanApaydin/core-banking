package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.common.application.port.out.TokenBlacklistPort;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider JwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistPort tokenBlacklistPort;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    private JwtAuthenticationFilter filter;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(JwtTokenProvider, userDetailsService, tokenBlacklistPort);
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
        verifyNoInteractions(JwtTokenProvider, userDetailsService, tokenBlacklistPort);
    }

    @Test
    void shouldContinueChainWhenAuthHeaderDoesNotStartWithBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic userpass");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(JwtTokenProvider, userDetailsService, tokenBlacklistPort);
    }

    @Test
    void shouldSend401WhenJwtServiceThrowsException() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalidjwt");
        when(JwtTokenProvider.extractUsername("invalidjwt")).thenThrow(new RuntimeException("invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldContinueChainWhenUsernameIsNull() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");

        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldSkipAuthenticationWhenTokenIsInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(userDetailsService.loadUserByUsername("user")).thenReturn(userDetails);
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(userDetailsService.loadUserByUsername("user")).thenReturn(userDetails);
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);
        when(userDetails.getAuthorities()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        assertNotNull(auth.getDetails());
    }

    @Test
    void shouldSend401OnBearerTokenWithMalformedJwt() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(JwtTokenProvider.extractUsername("")).thenThrow(new RuntimeException("JWT string is empty"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should send 401 when token is blacklisted")
    void shouldSend401WhenTokenIsBlacklisted() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer blacklisted-token");
        when(tokenBlacklistPort.isBlacklisted("blacklisted-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(JwtTokenProvider, userDetailsService);
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
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("should fallback to UserDetailsService when JWT has no role claim")
    void shouldFallbackToUserDetailsServiceWhenNoRoleClaim() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.extractUsername("token")).thenReturn("user");
        when(JwtTokenProvider.extractUserId("token")).thenReturn(42L);
        when(JwtTokenProvider.extractRole("token")).thenReturn(null);
        when(userDetailsService.loadUserByUsername("user")).thenReturn(userDetails);
        when(JwtTokenProvider.isTokenValid("token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        verify(userDetailsService).loadUserByUsername("user");
    }
}
