package com.bank.app.user.adapter.in.web;

import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.JwtPort.VerifiedToken;
import com.bank.app.user.config.BrowserSessionProperties;
import com.bank.app.user.config.SessionTokenLifetimeProperties;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.RefreshTokenReuseException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the browser-cookie flow (PIT: ~28 mutants lived here).
 * Pure Mockito + Spring mock servlet objects — no container, deterministic.
 */
@ExtendWith(MockitoExtension.class)
class BrowserAuthControllerTest {

    @Mock private LoginUserUseCase loginUserUseCase;
    @Mock private LogoutUseCase logoutUseCase;
    @Mock private RefreshSessionUseCase refreshSessionUseCase;
    @Mock private ClientIpResolverPort clientIpResolver;
    @Mock private CsrfBindingPort csrfBinding;
    @Mock private JwtPort jwtPort;
    @Mock private UserContextService userContextService;

    private BrowserAuthController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = new BrowserAuthController(loginUserUseCase, logoutUseCase,
                refreshSessionUseCase, clientIpResolver, csrfBinding, jwtPort,
                userContextService, new BrowserSessionProperties(false),
                new SessionTokenLifetimeProperties(900000L, 604800000L));
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest requestWithCookies(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        return request;
    }

    @Test
    void loginSetsThreeCookiesAndReturnsPrincipal() {
        when(clientIpResolver.resolveClientIp(any(), any())).thenReturn("10.0.0.1");
        when(loginUserUseCase.execute(eq(new AuthRequest("alice", "Secret123456", null, null)), eq("10.0.0.1")))
                .thenReturn(new AuthResponse("access", "refresh", 7L, "alice", 900000L));
        when(csrfBinding.issueCsrfToken("7")).thenReturn("csrf-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<BrowserAuthController.BrowserUser> result = controller.login(
                new AuthWebRequest("alice", "Secret123456"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(new BrowserAuthController.BrowserUser(7L, "alice"));
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        verify(csrfBinding).issueCsrfToken("7");
    }

    @Test
    void refreshRejectsMissingCsrfPairBeforeAnyTokenWork() {
        MockHttpServletRequest request = requestWithCookies();

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshRejectsMissingRefreshCookie() {
        MockHttpServletRequest request = requestWithCookies(new Cookie("BANK_CSRF", "c"));
        request.addHeader("X-CSRF-Token", "c");

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshClearsCookiesWhenTokenUnverifiable() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "stale"));
        request.addHeader("X-CSRF-Token", "c");
        when(jwtPort.verifyAndDecode("stale")).thenReturn(null);

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }

    @Test
    void refreshRejectsCsrfMismatch() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "other"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(jwtPort.verifyAndDecode("rt"))
                .thenReturn(new VerifiedToken("alice", 7L, "ROLE_USER", "jti", 999L));
        when(csrfBinding.verifyCsrfToken("c", "other", "7")).thenReturn(false);

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshClearsCookiesWhenUseCaseRejects() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(jwtPort.verifyAndDecode("rt"))
                .thenReturn(new VerifiedToken("alice", 7L, "ROLE_USER", "jti", 999L));
        when(csrfBinding.verifyCsrfToken("c", "c", "7")).thenReturn(true);
        when(refreshSessionUseCase.execute("rt"))
                .thenThrow(new AuthenticationFailedException("expired"));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }

    @Test
    void refreshClearsCookiesOnReuse() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(jwtPort.verifyAndDecode("rt"))
                .thenReturn(new VerifiedToken("alice", 7L, "ROLE_USER", "jti", 999L));
        when(csrfBinding.verifyCsrfToken("c", "c", "7")).thenReturn(true);
        when(refreshSessionUseCase.execute("rt"))
                .thenThrow(new RefreshTokenReuseException());

        assertThat(controller.refresh(request, response).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesCookiesOnSuccess() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(jwtPort.verifyAndDecode("rt"))
                .thenReturn(new VerifiedToken("alice", 7L, "ROLE_USER", "jti", 999L));
        when(csrfBinding.verifyCsrfToken("c", "c", "7")).thenReturn(true);
        when(refreshSessionUseCase.execute("rt"))
                .thenReturn(new AuthResponse("new-access", "new-refresh", 7L, "alice", 900000L));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .isEqualTo(new BrowserAuthController.BrowserUser(7L, "alice"));
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(2);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void sessionRejectsMissingPrincipal() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.session(new MockHttpServletRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sessionReturnsPrincipal() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("alice"));

        ResponseEntity<?> result = controller.session(new MockHttpServletRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .isEqualTo(new BrowserAuthController.BrowserUser(7L, "alice"));
    }

    @Test
    void logoutDelegatesBearerTokenAndAlwaysClearsCookies() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_SESSION", "sess"), new Cookie("BANK_REFRESH", "rt"));

        ResponseEntity<Void> result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(logoutUseCase).execute("Bearer sess", "rt");
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
        // Kills the setHeader(CACHE_CONTROL) VoidMethodCall mutant.
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void logoutPassesNullTokensWhenCookiesAbsent() {
        // Kills both EmptyObjectReturn mutants on cookieValue (null -> ""):
        // absent cookies must stay null, not empty strings. The bare request
        // has getCookies() == null (line 195); the empty-array variant below
        // covers the not-found tail (line 199).
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.logout(request, response);

        verify(logoutUseCase).execute(
                isNull(), isNull());
    }

    @Test
    void logoutPassesNullForMissingRefreshCookie() {
        MockHttpServletRequest request = requestWithCookies(new Cookie("BANK_SESSION", "sess"));

        controller.logout(request, response);

        verify(logoutUseCase).execute("Bearer sess", null);
    }

    @Test
    void logoutClearsCookiesEvenWhenRevocationFails() {
        MockHttpServletRequest request = requestWithCookies();
        doThrow(new RuntimeException("store down")).when(logoutUseCase).execute(any(), any());

        assertThatThrownBy(() -> controller.logout(request, response))
                .isInstanceOf(RuntimeException.class);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }
}
