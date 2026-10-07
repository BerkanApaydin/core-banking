package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.Cookie;
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

    private static JwtPort.VerifiedToken validToken() {
        return new JwtPort.VerifiedToken("user", 42L, "ROLE_USER", "jti", System.currentTimeMillis() + 60_000);
    }

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
        when(request.getServletPath()).thenReturn("/");

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
        when(JwtTokenProvider.verifyAndDecode("invalidjwt")).thenThrow(new RuntimeException("invalid token"));

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectRefreshTokenOnApiPath() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer refreshtoken");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(JwtTokenProvider.verifyAndDecode("refreshtoken")).thenReturn(validToken());
        when(JwtTokenProvider.extractTokenType("refreshtoken")).thenReturn("refresh");

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldStepAsideOnRefreshEndpoints() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/auth/browser/refresh");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(JwtTokenProvider, tokenBlacklistPort);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueChainWhenUsernameIsNull() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(validToken());
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
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(validToken());

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
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(validToken());

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
        when(JwtTokenProvider.verifyAndDecode("blacklisted-token")).thenReturn(validToken());
        when(tokenBlacklistPort.isBlacklisted("blacklisted-token")).thenReturn(true);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        // Revoked and invalid tokens share one message (no revocation oracle).
        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAllowRepeatedLogoutForAlreadyRevokedSignedToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/api/v1/auth/logout");
        when(JwtTokenProvider.verifyAndDecode("revoked-token")).thenReturn(validToken());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenBlacklistPort);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldStillCheckRevocationForOtherMethodsAtLogoutPath() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-token");
        when(request.getMethod()).thenReturn("GET");
        when(JwtTokenProvider.verifyAndDecode("revoked-token")).thenReturn(validToken());
        when(tokenBlacklistPort.isBlacklisted("revoked-token")).thenReturn(true);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void browserCookieRequiresCsrfOnMutation() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/v1/accounts");
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("BANK_SESSION", "signed-token") });
        when(request.getMethod()).thenReturn("POST");
        when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 403, "ACCESS_DENIED", "Invalid browser CSRF token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void browserCookieWithMatchingCsrfAuthenticates() throws Exception {
        String csrf = "a".repeat(43);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/v1/accounts");
        when(request.getHeader("X-CSRF-Token")).thenReturn(csrf);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("BANK_SESSION", "signed-token"), new Cookie("BANK_CSRF", csrf)
        });
        when(request.getMethod()).thenReturn("POST");
        when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void browserCookieRejectsMismatchedCsrf() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/v1/accounts");
        when(request.getHeader("X-CSRF-Token")).thenReturn("a".repeat(43));
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("BANK_SESSION", "signed-token"), new Cookie("BANK_CSRF", "b".repeat(43))
        });
        when(request.getMethod()).thenReturn("POST");
        when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 403, "ACCESS_DENIED", "Invalid browser CSRF token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void secureBrowserModeIgnoresDevelopmentCookieName() throws Exception {
        filter = new JwtAuthenticationFilter(JwtTokenProvider, tokenBlacklistPort,
                new ObjectMapper(),
                new JwtAuthenticationFilter.PlainDoubleSubmitCsrfBinding(),
                new BrowserSessionCookieProperties(true));
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/v1/accounts");
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("BANK_SESSION", "old-token") });

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(JwtTokenProvider);
    }

    @Test
    void expiredBrowserCookieDoesNotBlockPublicApplicationShell() throws Exception {
        when(request.getServletPath()).thenReturn("/");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getCookies();
        verifyNoInteractions(JwtTokenProvider, tokenBlacklistPort);
    }

    @Test
    void invalidBrowserCookieStillReturns401ForProtectedApi() throws Exception {
        when(request.getServletPath()).thenReturn("/api/v1/accounts");
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("BANK_SESSION", "expired-token") });
        when(JwtTokenProvider.verifyAndDecode("expired-token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldSend503WhenRevocationStoreCannotVerifyToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(JwtTokenProvider.verifyAndDecode("valid-token")).thenReturn(validToken());
        when(tokenBlacklistPort.isBlacklisted("valid-token"))
                .thenThrow(new RevocationStoreUnavailableException(new RuntimeException("secret Redis detail")));

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 503, "SECURITY_BACKEND_UNAVAILABLE",
                "Security service temporarily unavailable");
        assertFalse(errorResponse.getContentAsString().contains("secret Redis detail"));
        verifyNoInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should authenticate from JWT claims when userId and role are present")
    void shouldAuthenticateFromJwtClaims() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(validToken());

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
        when(JwtTokenProvider.verifyAndDecode("token")).thenReturn(null);

        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, errorResponse, filterChain);

        assertProblemResponse(errorResponse, 401, "AUTHENTICATION_FAILED", "Invalid or expired token");
        verifyNoMoreInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Nested
    @DisplayName("session-bound CSRF (K7/D8)")
    class BoundCsrf {

        private com.bank.app.user.application.port.out.CsrfBindingPort binding;
        private jakarta.servlet.http.Cookie csrfCookie;

        @BeforeEach
        void setUpBound() {
            binding = new com.bank.app.infrastructure.adapter.out.security.HmacCsrfBindingAdapter(
                    "test-only-csrf-mac-key-32bytes!!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            filter = new JwtAuthenticationFilter(JwtTokenProvider, tokenBlacklistPort,
                    new ObjectMapper(), binding, new BrowserSessionCookieProperties(false));
            // validToken() carries userId 42: mint the cookie for that identity.
            String bound = binding.issueCsrfToken("42");
            csrfCookie = new jakarta.servlet.http.Cookie("BANK_CSRF", bound);
        }

        @Test
        @DisplayName("should accept a bound token minted for the token identity")
        void shouldAcceptBoundToken() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getServletPath()).thenReturn("/api/v1/accounts");
            when(request.getHeader("X-CSRF-Token")).thenReturn(csrfCookie.getValue());
            when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[] {
                    new jakarta.servlet.http.Cookie("BANK_SESSION", "signed-token"), csrfCookie
            });
            when(request.getMethod()).thenReturn("POST");
            when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should reject a transplanted token minted for another user")
        void shouldRejectTransplantedToken() throws Exception {
            String foreign = binding.issueCsrfToken("777");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getServletPath()).thenReturn("/api/v1/accounts");
            when(request.getHeader("X-CSRF-Token")).thenReturn(foreign);
            when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[] {
                    new jakarta.servlet.http.Cookie("BANK_SESSION", "signed-token"),
                    new jakarta.servlet.http.Cookie("BANK_CSRF", foreign)
            });
            when(request.getMethod()).thenReturn("POST");
            when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

            MockHttpServletResponse errorResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, errorResponse, filterChain);

            assertProblemResponse(errorResponse, 403, "ACCESS_DENIED", "Invalid browser CSRF token");
            verifyNoInteractions(filterChain);
        }

        @Test
        @DisplayName("should reject a forged equal pair without a valid MAC")
        void shouldRejectForgedEqualPair() throws Exception {
            String forged = "c".repeat(87);
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getServletPath()).thenReturn("/api/v1/accounts");
            when(request.getHeader("X-CSRF-Token")).thenReturn(forged);
            when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[] {
                    new jakarta.servlet.http.Cookie("BANK_SESSION", "signed-token"),
                    new jakarta.servlet.http.Cookie("BANK_CSRF", forged)
            });
            when(request.getMethod()).thenReturn("POST");
            when(JwtTokenProvider.verifyAndDecode("signed-token")).thenReturn(validToken());

            MockHttpServletResponse errorResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, errorResponse, filterChain);

            assertProblemResponse(errorResponse, 403, "ACCESS_DENIED", "Invalid browser CSRF token");
            verifyNoInteractions(filterChain);
        }
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
