package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.support.ProblemResponses;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.RefreshTokenReuseException;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.config.BrowserSessionProperties;
import com.bank.app.user.config.SessionTokenLifetimeProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Same-origin browser session; the existing bearer API remains available to API clients. */
@RestController
@ApiVersion("v1")
@RequestMapping("/auth/browser")
public class BrowserAuthController {
    private final LoginUserUseCase loginUserUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final ClientIpResolverPort clientIpResolver;
    private final CsrfBindingPort csrfBinding;
    private final JwtPort jwtPort;
    private final BrowserSessionCookies cookies;
    private final UserContextService userContextService;
    private final Duration sessionLifetime;
    private final Duration refreshLifetime;

    public BrowserAuthController(LoginUserUseCase loginUserUseCase, LogoutUseCase logoutUseCase,
            RefreshSessionUseCase refreshSessionUseCase,
            ClientIpResolverPort clientIpResolver,
            CsrfBindingPort csrfBinding,
            JwtPort jwtPort,
            UserContextService userContextService,
            BrowserSessionProperties browserSession,
            SessionTokenLifetimeProperties lifetimes) {
        this.loginUserUseCase = loginUserUseCase;
        this.logoutUseCase = logoutUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.clientIpResolver = clientIpResolver;
        this.csrfBinding = csrfBinding;
        this.jwtPort = jwtPort;
        this.cookies = new BrowserSessionCookies(browserSession.secure());
        this.userContextService = userContextService;
        this.sessionLifetime = Duration.ofMillis(lifetimes.accessExpiration());
        this.refreshLifetime = Duration.ofMillis(lifetimes.refreshExpiration());
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserUser> login(@Valid @RequestBody AuthWebRequest webRequest,
            HttpServletRequest request, HttpServletResponse response) {
        AuthRequest command = new AuthRequest(webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        String ip = clientIpResolver.resolveClientIp(
                request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
        AuthResponse authenticated = loginUserUseCase.execute(command, ip);
        // K7/D8: CSRF bound to the server-verified user id (not the token, so
        // the same cookie stays valid across access/refresh rotation while a
        // cookie transplanted from another user fails verification).
        String csrf = csrfBinding.issueCsrfToken(String.valueOf(authenticated.userId()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookies.sessionCookieName(),
                authenticated.token(), true, sessionLifetime));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(authenticated.refreshToken()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookies.csrfCookieName(),
                csrf, false, sessionLifetime));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.ok(new BrowserUser(authenticated.userId(), authenticated.username()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        // The filter steps aside on this path (no session required); CSRF is
        // enforced here instead. Bound to the server-verified user id (K7/D8):
        // the session JWT may be expired exactly when rotation is legitimate,
        // so the subject comes from the refresh token's own verified claims.
        // Missing header/cookie fails closed before any token work (403); an
        // unverifiable token yields the same 401 the use case would produce.
        // Bodies are RFC 7807 ProblemDetail (same shape as the global handler),
        // not empty responses, so browser clients need a single error parser.
        String header = request.getHeader(BrowserSessionCookies.CSRF_HEADER);
        String csrfCookie = cookieValue(request, cookies.csrfCookieName());
        if (header == null || csrfCookie == null) {
            return ProblemResponses.forbidden("Browser CSRF token is missing.", request.getRequestURI());
        }
        String refreshToken = cookieValue(request, cookies.refreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            return ProblemResponses.unauthorized(
                    "Browser session has expired. Please log in again.", request.getRequestURI());
        }
        JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(refreshToken);
        if (verified == null || verified.userId() == null) {
            clearSessionCookies(response);
            return ProblemResponses.unauthorized(
                    "Browser session has expired. Please log in again.", request.getRequestURI());
        }
        if (!csrfBinding.verifyCsrfToken(header, csrfCookie, String.valueOf(verified.userId()))) {
            return ProblemResponses.forbidden("Invalid browser CSRF token.", request.getRequestURI());
        }
        final AuthResponse rotated;
        try {
            rotated = refreshSessionUseCase.execute(refreshToken);
        } catch (AuthenticationFailedException | RefreshTokenReuseException e) {
            clearSessionCookies(response);
            return ProblemResponses.unauthorized(
                    "Browser session has expired. Please log in again.", request.getRequestURI());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookies.sessionCookieName(),
                rotated.token(), true, sessionLifetime));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(rotated.refreshToken()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.ok(new BrowserUser(rotated.userId(), rotated.username()));
    }

    @GetMapping("/session")
    public ResponseEntity<?> session(HttpServletRequest request) {
        // No orElseThrow(): an absent principal means "not logged in" (401),
        // not a server bug. Throwing NoSuchElementException here used to fall
        // through to the 500 fallback because no handler maps it.
        Long userId = userContextService.getCurrentUserId().orElse(null);
        String username = userContextService.getCurrentUsername().orElse(null);
        if (userId == null || username == null) {
            return ProblemResponses.unauthorized("No active browser session.", request.getRequestURI());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new BrowserUser(userId, username));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request, cookies.sessionCookieName());
        String refreshToken = cookieValue(request, cookies.refreshCookieName());
        try {
            logoutUseCase.execute(token != null ? "Bearer " + token : null, refreshToken);
            return ResponseEntity.noContent().build();
        } finally {
            clearSessionCookies(response);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }

    private void clearSessionCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(cookies.sessionCookieName(), "", true, Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie("", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(cookies.csrfCookieName(), "", false, Duration.ZERO));
    }

    private String cookie(String name, String value, boolean httpOnly, Duration age) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly).secure(cookies.secure()).sameSite("Strict")
                .path("/").maxAge(age).build().toString();
    }

    /**
     * Refresh cookie scoped to the refresh endpoint path: browsers only send
     * it there, never on API calls (least exposure for the long-lived token).
     */
    private String refreshCookie(String value) {
        return refreshCookie(value, refreshLifetime);
    }

    private String refreshCookie(String value, Duration age) {
        return ResponseCookie.from(cookies.refreshCookieName(), value)
                .httpOnly(true).secure(cookies.secure()).sameSite("Strict")
                .path("/api/v1/auth/browser/refresh").maxAge(age).build().toString();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public record BrowserUser(Long userId, String username) {}
}
