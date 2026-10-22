package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(jwtPort.extractTokenType("jwt")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

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
        when(jwtPort.extractTokenType("jwt")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

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
}
