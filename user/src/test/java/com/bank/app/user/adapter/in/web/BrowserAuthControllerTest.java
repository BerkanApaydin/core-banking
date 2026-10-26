package com.bank.app.user.adapter.in.web;

import com.bank.app.common.application.exception.AuthenticationRequiredException;
import com.bank.app.common.application.exception.CsrfViolationException;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
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
import org.springframework.http.ProblemDetail;
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
import static org.mockito.Mockito.never;
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
    @Mock private UserContextService userContextService;

    private BrowserAuthController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = new BrowserAuthController(loginUserUseCase, logoutUseCase,
                refreshSessionUseCase, clientIpResolver, csrfBinding,
                userContextService, new BrowserSessionProperties(false),
                new SessionTokenLifetimeProperties(900000L, 604800000L));
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest requestWithCookies(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        return request;
    }

    private BrowserAuthController secureController() {
        return new BrowserAuthController(loginUserUseCase, logoutUseCase,
                refreshSessionUseCase, clientIpResolver, csrfBinding,
                userContextService, new BrowserSessionProperties(true),
                new SessionTokenLifetimeProperties(900000L, 604800000L));
    }

    private static String cookieHeader(List<String> headers, String prefix) {
        return headers.stream().filter(header -> header.startsWith(prefix))
                .findFirst().orElseThrow();
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
    void loginIssuesHostPrefixedSecureCookiesWithScopedLifetimes() {
        // R-T02: the production (secure) cookie contract — __Host- session and
        // CSRF names, __Secure- path-scoped refresh name, Secure on all three,
        // HttpOnly on everything except the JS-readable CSRF cookie.
        when(clientIpResolver.resolveClientIp(any(), any())).thenReturn("10.0.0.1");
        when(loginUserUseCase.execute(any(AuthRequest.class), eq("10.0.0.1")))
                .thenReturn(new AuthResponse("access", "refresh", 7L, "alice", 900000L));
        when(csrfBinding.issueCsrfToken("7")).thenReturn("csrf-1");
        MockHttpServletResponse secureResponse = new MockHttpServletResponse();

        secureController().login(new AuthWebRequest("alice", "Secret123456"),
                new MockHttpServletRequest(), secureResponse);

        List<String> headers = secureResponse.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(3);
        String session = cookieHeader(headers, "__Host-BANK_SESSION=");
        String refresh = cookieHeader(headers, "__Secure-BANK_REFRESH=");
        String csrf = cookieHeader(headers, "__Host-BANK_CSRF=");
        assertThat(session).contains("__Host-BANK_SESSION=access", "Secure", "HttpOnly",
                "SameSite=Strict", "Path=/", "Max-Age=900");
        assertThat(refresh).contains("__Secure-BANK_REFRESH=refresh", "Secure", "HttpOnly",
                "SameSite=Strict", "Path=/api/v1/auth/browser", "Max-Age=604800");
        assertThat(csrf).contains("__Host-BANK_CSRF=csrf-1", "Secure", "SameSite=Strict",
                "Path=/", "Max-Age=604800");
        assertThat(csrf).doesNotContain("HttpOnly");
    }

    @Test
    void refreshReissuesFreshCsrfCookieWithRefreshLifetimeInSecureMode() {
        // R-T03: after the 15-minute access token expires, the refresh call
        // must still have a valid CSRF pair (cookie lifetime = refresh
        // lifetime) and must rotate the value for the next rotation.
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("__Host-BANK_CSRF", "old-csrf"),
                new Cookie("__Secure-BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "old-csrf");
        when(csrfBinding.issueCsrfToken("7")).thenReturn("new-csrf");
        when(refreshSessionUseCase.executeWithCsrf("rt", "old-csrf", "old-csrf"))
                .thenReturn(new AuthResponse("new-access", "new-refresh", 7L, "alice", 900000L));
        MockHttpServletResponse secureResponse = new MockHttpServletResponse();

        ResponseEntity<?> result = secureController().refresh(request, secureResponse);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> headers = secureResponse.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(3);
        assertThat(cookieHeader(headers, "__Host-BANK_SESSION=new-access"))
                .contains("Max-Age=900");
        assertThat(cookieHeader(headers, "__Secure-BANK_REFRESH=new-refresh"))
                .contains("Max-Age=604800");
        assertThat(cookieHeader(headers, "__Host-BANK_CSRF=new-csrf"))
                .contains("Max-Age=604800").doesNotContain("HttpOnly");
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
    void refreshRejectsHeaderWithoutCsrfCookie() {
        MockHttpServletRequest request = requestWithCookies(new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refreshSessionUseCase, never()).executeWithCsrf(anyString(), anyString(), anyString());
    }

    @Test
    void refreshRejectsBlankRefreshCookie() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "  "));
        request.addHeader("X-CSRF-Token", "c");

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshSessionUseCase, never()).executeWithCsrf(anyString(), anyString(), anyString());
    }

    @Test
    void refreshClearsCookiesWhenVerifiedTokenHasNoUserId() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenThrow(new AuthenticationFailedException("Invalid refresh token."));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }

    @Test
    void refreshClearsCookiesWhenTokenUnverifiable() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "stale"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("stale", "c", "c"))
                .thenThrow(new AuthenticationFailedException("Invalid refresh token."));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }

    @Test
    void refreshPropagatesCsrfViolationWithoutClearingCookies() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "other"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "other"))
                .thenThrow(new CsrfViolationException());

        assertThatThrownBy(() -> controller.refresh(request, response))
                .isInstanceOf(CsrfViolationException.class);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void refreshClearsCookiesWhenUseCaseRejects() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenThrow(new AuthenticationFailedException("expired"));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
    }

    @Test
    void refreshReturns503AndKeepsCookiesWhenBackendUnavailable() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenThrow(new AuthenticationBackendUnavailableException(
                        new IllegalStateException("db down")));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(((ProblemDetail) result.getBody()).getProperties())
                .containsEntry("code", "SECURITY_BACKEND_UNAVAILABLE");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void refreshClearsCookiesOnReuse() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenThrow(new RefreshTokenReuseException());

        assertThat(controller.refresh(request, response).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshDoesNotClearCookiesOnUnexpectedFailure() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> controller.refresh(request, response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void refreshRotatesCookiesOnSuccess() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_CSRF", "c"), new Cookie("BANK_REFRESH", "rt"));
        request.addHeader("X-CSRF-Token", "c");
        when(csrfBinding.issueCsrfToken("7")).thenReturn("csrf-1");
        when(refreshSessionUseCase.executeWithCsrf("rt", "c", "c"))
                .thenReturn(new AuthResponse("new-access", "new-refresh", 7L, "alice", 900000L));

        ResponseEntity<?> result = controller.refresh(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .isEqualTo(new BrowserAuthController.BrowserUser(7L, "alice"));
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
        verify(csrfBinding).issueCsrfToken("7");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void sessionRejectsMissingPrincipal() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.session())
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void sessionRejectsPrincipalWithoutUsername() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(userContextService.getCurrentUsername()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.session())
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void sessionReturnsPrincipal() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("alice"));

        ResponseEntity<?> result = controller.session();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .isEqualTo(new BrowserAuthController.BrowserUser(7L, "alice"));
    }

    @Test
    void logoutDelegatesBearerTokenAndClearsCookiesOnSuccess() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_SESSION", "sess"), new Cookie("BANK_REFRESH", "rt"));

        ResponseEntity<?> result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(logoutUseCase).execute("Bearer sess", "rt");
        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(3);
        // Kills the setHeader(CACHE_CONTROL) VoidMethodCall mutant.
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void secureLogoutClearsHostAndSecurePrefixedCookies() {
        // R-U02: the production cookie contract must also cover logout. A
        // deletion header with the wrong name or path leaves the credential
        // in the browser even though the server-side session is gone.
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("__Host-BANK_SESSION", "sess"),
                new Cookie("__Secure-BANK_REFRESH", "rt"),
                new Cookie("__Host-BANK_CSRF", "csrf"));
        MockHttpServletResponse secureResponse = new MockHttpServletResponse();

        ResponseEntity<?> result = secureController().logout(request, secureResponse);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(logoutUseCase).execute("Bearer sess", "rt");
        List<String> headers = secureResponse.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(3);
        assertThat(cookieHeader(headers, "__Host-BANK_SESSION="))
                .contains("Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict", "Path=/");
        assertThat(cookieHeader(headers, "__Secure-BANK_REFRESH="))
                .contains("Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict",
                        "Path=/api/v1/auth/browser");
        assertThat(cookieHeader(headers, "__Host-BANK_CSRF="))
                .contains("Max-Age=0", "Secure", "SameSite=Strict", "Path=/")
                .doesNotContain("HttpOnly");
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
    void logoutReturns503AndKeepsCookiesWhenBackendUnavailable() {
        MockHttpServletRequest request = requestWithCookies(
                new Cookie("BANK_SESSION", "sess"), new Cookie("BANK_REFRESH", "rt"));
        doThrow(new AuthenticationBackendUnavailableException(
                new IllegalStateException("db down")))
                .when(logoutUseCase).execute(any(), any());

        ResponseEntity<?> result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(((ProblemDetail) result.getBody()).getProperties())
                .containsEntry("code", "SECURITY_BACKEND_UNAVAILABLE");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void logoutKeepsCookiesWhenRevocationStoreUnavailable() {
        MockHttpServletRequest request = requestWithCookies();
        doThrow(new RevocationStoreUnavailableException(
                new IllegalStateException("redis down")))
                .when(logoutUseCase).execute(any(), any());

        ResponseEntity<?> result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void logoutDoesNotClearCookiesOnUnexpectedFailure() {
        MockHttpServletRequest request = requestWithCookies();
        doThrow(new RuntimeException("boom")).when(logoutUseCase).execute(any(), any());

        assertThatThrownBy(() -> controller.logout(request, response))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
