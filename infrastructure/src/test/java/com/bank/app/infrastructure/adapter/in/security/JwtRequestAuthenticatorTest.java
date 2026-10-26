package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class JwtRequestAuthenticatorTest {

    @Mock
    private JwtPort jwtPort;
    @Mock
    private TokenBlacklistPort tokenBlacklistPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private CsrfBindingPort csrfBinding;

    private JwtRequestAuthenticator authenticator;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        authenticator = new JwtRequestAuthenticator(jwtPort, tokenBlacklistPort, loadUserPort,
                csrfBinding, new BrowserSessionCookies(false));
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    private static JwtPort.VerifiedToken token(Long userId, String role) {
        return new JwtPort.VerifiedToken("user", userId, role, "jti",
                System.currentTimeMillis() + 60_000);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void shouldRejectUnverifiableTokenOutsideLogout() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(null);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("GET", "/api/v1/transfers"), "/api/v1/transfers", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldPassThroughUnverifiableTokenOnLogout() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(null);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", "/api/v1/auth/logout"), "/api/v1/auth/logout", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.PASS_THROUGH, result);
    }

    @Test
    void shouldRejectBlacklistedToken() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(tokenBlacklistPort.isBlacklisted("jwt")).thenReturn(true);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("GET", "/api/v1/transfers"), "/api/v1/transfers", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldRejectRefreshTokenOutsideRefreshEndpoint() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(jwtPort.extractTokenType("jwt")).thenReturn(JwtPort.TOKEN_TYPE_REFRESH);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("GET", "/api/v1/transfers"), "/api/v1/transfers", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldAllowRefreshTokenOnRefreshEndpoint() {
        // The refresh endpoint is the one place a refresh token authenticates;
        // the type check must not turn a legitimate rotation into a 401.
        String path = "/api/v1/auth/refresh";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(jwtPort.extractTokenType("jwt")).thenReturn(JwtPort.TOKEN_TYPE_REFRESH);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.AUTHENTICATED, result);
    }

    @Test
    void shouldRejectVerifiedTokenWithoutUserId() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(null, "ROLE_USER"));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("GET", "/api/v1/transfers"), "/api/v1/transfers", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldRejectVerifiedTokenWithoutRole() {
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, null));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("GET", "/api/v1/transfers"), "/api/v1/transfers", "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldRejectAdminTokenWithStaleTokenVersion() {
        String path = "/api/v1/admin/accounts";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_ADMIN"));
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(3L);
        when(loadUserPort.findTokenVersionById(7L)).thenReturn(Optional.of(4L));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.UNAUTHORIZED, result);
    }

    @Test
    void shouldReturnBackendUnavailableWhenAdminStoreFails() {
        String path = "/api/v1/admin/accounts";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_ADMIN"));
        when(loadUserPort.findTokenVersionById(7L)).thenThrow(new RuntimeException("db down"));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.BACKEND_UNAVAILABLE, result);
    }

    // --- CSRF enforcement (browser-session requests only) ---

    @ParameterizedTest(name = "{0} without a CSRF token is forbidden")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void shouldRejectBrowserSessionMutationWithoutCsrfToken(String method) {
        // A cross-site form post carries the session cookie but no CSRF header:
        // without this check such a request would move money.
        String path = "/api/v1/transfers";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(csrfBinding.verifyCsrfToken(any(), any(), eq("7"))).thenReturn(false);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request(method, path), path, "jwt", false);

        assertEquals(JwtRequestAuthenticator.Result.FORBIDDEN, result);
    }

    @Test
    void shouldBindTheCsrfCheckToTheServerVerifiedUserId() {
        // The binding subject is the server-verified user id, never a
        // client-supplied one: a token minted for another user must not pass.
        String path = "/api/v1/transfers";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(csrfBinding.verifyCsrfToken(any(), any(), eq("7"))).thenReturn(false);

        authenticator.authenticate(request("POST", path), path, "jwt", false);

        verify(csrfBinding).verifyCsrfToken(null, null, "7");
    }

    @Test
    void shouldAuthenticateBrowserSessionMutationWithAcceptedCsrfToken() {
        String path = "/api/v1/transfers";
        String csrf = "a".repeat(43);
        MockHttpServletRequest request = request("POST", path);
        request.addHeader(BrowserSessionCookies.CSRF_HEADER, csrf);
        request.setCookies(new Cookie("BANK_CSRF", csrf));
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(csrfBinding.verifyCsrfToken(csrf, csrf, "7")).thenReturn(true);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request, path, "jwt", false);

        assertEquals(JwtRequestAuthenticator.Result.AUTHENTICATED, result);
    }

    @Test
    void shouldRejectBrowserSessionMutationWhenCsrfBindingRejectsThePair() {
        String path = "/api/v1/transfers";
        String csrf = "a".repeat(43);
        MockHttpServletRequest request = request("POST", path);
        request.addHeader(BrowserSessionCookies.CSRF_HEADER, csrf);
        request.setCookies(new Cookie("BANK_CSRF", csrf));
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));
        when(csrfBinding.verifyCsrfToken(csrf, csrf, "7")).thenReturn(false);

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request, path, "jwt", false);

        assertEquals(JwtRequestAuthenticator.Result.FORBIDDEN, result);
    }

    @Test
    void shouldNotCheckCsrfForBearerAuthenticatedRequests() {
        // Bearer clients are not cookie-based, so they are immune to CSRF;
        // consulting the binding port would only add a dependency (and a
        // spurious 403) to the API path.
        String path = "/api/v1/transfers";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.AUTHENTICATED, result);
        verify(csrfBinding, never()).verifyCsrfToken(any(), any(), any());
    }

    @ParameterizedTest(name = "{0} does not require a CSRF token")
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldNotRequireCsrfForSafeMethods(String method) {
        String path = "/api/v1/transfers";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_USER"));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request(method, path), path, "jwt", false);

        assertEquals(JwtRequestAuthenticator.Result.AUTHENTICATED, result);
        verify(csrfBinding, never()).verifyCsrfToken(any(), any(), any());
    }

    // --- admin MFA gate ---

    @Test
    void shouldAuthenticateAdminWithCurrentTokenVersionAndEnrolledMfa() {
        String path = "/api/v1/admin/accounts";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_ADMIN"));
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(3L);
        when(loadUserPort.findTokenVersionById(7L)).thenReturn(Optional.of(3L));
        when(loadUserPort.findMfaEnabledById(7L)).thenReturn(Optional.of(true));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.AUTHENTICATED, result);
    }

    @Test
    void shouldRequireMfaEnrollmentForAdminWithoutMfa() {
        // An admin account without a second factor must not get a full admin
        // session: every admin path stays refused until enrollment.
        String path = "/api/v1/admin/accounts";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_ADMIN"));
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(3L);
        when(loadUserPort.findTokenVersionById(7L)).thenReturn(Optional.of(3L));
        when(loadUserPort.findMfaEnabledById(7L)).thenReturn(Optional.of(false));

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.MFA_REQUIRED, result);
    }

    @Test
    void shouldRequireMfaWhenTheAdminMfaProjectionIsEmpty() {
        // An unreadable/absent MFA flag must fail closed, never open: an
        // unknown admin account is refused, not granted.
        String path = "/api/v1/admin/accounts";
        when(jwtPort.verifyAndDecode("jwt")).thenReturn(token(7L, "ROLE_ADMIN"));
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(3L);
        when(loadUserPort.findTokenVersionById(7L)).thenReturn(Optional.of(3L));
        when(loadUserPort.findMfaEnabledById(7L)).thenReturn(Optional.empty());

        JwtRequestAuthenticator.Result result = authenticator.authenticate(
                request("POST", path), path, "jwt", true);

        assertEquals(JwtRequestAuthenticator.Result.MFA_REQUIRED, result);
    }
}
