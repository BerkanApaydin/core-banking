package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** Same-origin browser session; the existing bearer API remains available to API clients. */
@RestController
@ApiVersion("v1")
@RequestMapping("/auth/browser")
public class BrowserAuthController {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginUserUseCase loginUserUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ClientIpResolverPort clientIpResolver;
    private final BrowserSessionCookies cookies;
    private final UserContextService userContextService;
    private final Duration sessionLifetime;

    public BrowserAuthController(LoginUserUseCase loginUserUseCase, LogoutUseCase logoutUseCase,
            ClientIpResolverPort clientIpResolver,
            UserContextService userContextService,
            @Value("${app.security.browser-session.secure:false}") boolean secureCookies,
            @Value("${jwt.expiration:86400000}") long jwtExpirationMs) {
        this.loginUserUseCase = loginUserUseCase;
        this.logoutUseCase = logoutUseCase;
        this.clientIpResolver = clientIpResolver;
        this.cookies = new BrowserSessionCookies(secureCookies);
        this.userContextService = userContextService;
        this.sessionLifetime = Duration.ofMillis(jwtExpirationMs);
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserUser> login(@Valid @RequestBody AuthWebRequest webRequest,
            HttpServletRequest request, HttpServletResponse response) {
        AuthRequest command = new AuthRequest(webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        String ip = clientIpResolver.resolveClientIp(
                request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
        AuthResponse authenticated = loginUserUseCase.execute(command, ip);
        byte[] csrfBytes = new byte[32];
        RANDOM.nextBytes(csrfBytes);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookies.sessionCookieName(),
                authenticated.token(), true, sessionLifetime));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookies.csrfCookieName(),
                csrf, false, sessionLifetime));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.ok(new BrowserUser(authenticated.userId(), authenticated.username()));
    }

    @GetMapping("/session")
    public ResponseEntity<BrowserUser> session() {
        Long userId = userContextService.getCurrentUserId().orElseThrow();
        String username = userContextService.getCurrentUsername().orElseThrow();
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new BrowserUser(userId, username));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request, cookies.sessionCookieName());
        try {
            if (token != null) {
                logoutUseCase.execute("Bearer " + token);
            }
            return ResponseEntity.noContent().build();
        } finally {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    cookie(cookies.sessionCookieName(), "", true, Duration.ZERO));
            response.addHeader(HttpHeaders.SET_COOKIE,
                    cookie(cookies.csrfCookieName(), "", false, Duration.ZERO));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }

    private String cookie(String name, String value, boolean httpOnly, Duration age) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly).secure(cookies.secure()).sameSite("Strict")
                .path("/").maxAge(age).build().toString();
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
