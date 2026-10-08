package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.infrastructure.adapter.out.security.SimpleAuthenticatedPrincipal;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.domain.User;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.adapter.in.api.PublicApiPaths;
import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.Collections;
import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String MDC_USER_KEY = "userId";
    // Single generic message for revoked AND invalid tokens: distinct messages
    // let callers oracle whether a token was revoked (information disclosure).
    private static final String MSG_TOKEN_INVALID = "Invalid or expired token";
    // SEC-01: mirrors SecurityConfig's "/api/v1/admin/**" matcher (G-1 outer
    // layer). Requests under this prefix re-validate the token generation
    // against the DB (see hasCurrentTokenVersion).
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";

    private final JwtPort jwtPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final LoadUserPort loadUserPort;
    private final ObjectMapper objectMapper;
    private final BrowserSessionCookies browserSessionCookies;
    private final CsrfBindingPort csrfBinding;

    @Autowired
    public JwtAuthenticationFilter(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort, LoadUserPort loadUserPort,
            ObjectMapper objectMapper,
            CsrfBindingPort csrfBinding,
            BrowserSessionCookieProperties browserSession) {
        this.jwtPort = jwtPort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.loadUserPort = loadUserPort;
        this.objectMapper = objectMapper;
        this.csrfBinding = csrfBinding;
        this.browserSessionCookies = new BrowserSessionCookies(browserSession.secure());
    }

    // Isolated filter tests only (same package): falls back to the plain
    // double-submit check (no server-side binding). Package-private so Spring
    // can never select it for production wiring — prod must use the
    // @Autowired constructor above with the HMAC-bound CsrfBindingPort.
    JwtAuthenticationFilter(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort, LoadUserPort loadUserPort,
            ObjectMapper objectMapper) {
        this(jwtPort, tokenBlacklistPort, loadUserPort, objectMapper,
                new PlainDoubleSubmitCsrfBinding(),
                new BrowserSessionCookieProperties(false));
    }

    /** Test-only fallback: unsigned double-submit equality, no MAC binding. */
    static final class PlainDoubleSubmitCsrfBinding
            implements CsrfBindingPort {
        @Override
        public String issueCsrfToken(String bindingSubject) {
            throw new UnsupportedOperationException("test fallback cannot mint bound tokens");
        }

        @Override
        public boolean verifyCsrfToken(String header, String cookie, String bindingSubject) {
            return BrowserSessionCookies.validCsrfPair(header, cookie);
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        final boolean bearerAuth = authHeader != null && authHeader.startsWith(BEARER_PREFIX);
        final String path = requestPath(request);
        // Refresh endpoints own their authentication entirely (refresh token
        // in body or cookie, validated by the controller): an expired or
        // blacklisted access token in the header/cookie must not block a
        // legitimate rotation, so the filter steps aside completely here.
        if (isRefreshRequest(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }
        // The browser session cookie is for API calls. Browsers also send it
        // with / and static assets; an expired cookie must not prevent the
        // public application shell from loading so it can show the login UI.
        // Refresh endpoints authenticate with the refresh token itself (like
        // login authenticates with a password), so they never require a
        // valid session up front.
        final boolean browserAuth = authHeader == null
                && path.startsWith("/api/")
                && !isPublicLogin(request, path)
                && !isRefreshRequest(request, path);
        final String jwt = bearerAuth ? authHeader.substring(BEARER_PREFIX.length())
                : browserAuth ? cookieValue(request, browserSessionCookies.sessionCookieName()) : null;

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (jwt.isBlank()) {
            rejectUnauthorized(response, request.getRequestURI());
            return;
        }

        try {
            // Validate the signature FIRST so unauthenticated callers cannot use
            // this endpoint as a blacklist oracle or force Redis/Caffeine lookups
            // with arbitrary strings (DoS surface).
            JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(jwt);
            if (verified == null) {
                rejectUnauthorized(response, request.getRequestURI());
                return;
            }
            // A previously successful logout may lose its HTTP response. Let
            // only this exact action re-submit a still-valid signed token so
            // revocation can be retried idempotently; every other route must
            // continue enforcing the blacklist.
            if (!isLogoutRequest(request, path) && tokenBlacklistPort.isBlacklisted(jwt)) {
                rejectUnauthorized(response, request.getRequestURI());
                return;
            }
            // Refresh tokens authenticate only the refresh endpoints: a leaked
            // long-lived token must never pass as API authorization.
            if (JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtPort.extractTokenType(jwt))
                    && !isRefreshRequest(request, path)) {
                rejectUnauthorized(response, request.getRequestURI());
                return;
            }
            Long userId = verified.userId();
            String role = verified.role();
            // G-2 accepted trade-off (stateless JWT): role and userId come
            // from the verified access token without a per-request DB lookup.
            // A role demotion therefore takes effect on non-admin paths only
            // after the access token expires (default 15 min); admin paths
            // re-validate the token generation below (SEC-01), and the
            // refresh path (RefreshSessionUseCaseImpl) compares tokenVersion
            // against the DB and closes the window there. Short access TTL
            // bounds the residual delay.
            if (userId == null || role == null) {
                // Stateless JWT requires userId+role claims; legacy tokens without
                // claims are rejected instead of falling back to a DB lookup.
                // Clients must re-login to obtain a current token.
                rejectUnauthorized(response, request.getRequestURI());
                return;
            }
            // SEC-01: a demoted/suspended admin's pre-change access token
            // would otherwise stay valid until expiry. Admin calls are rare,
            // so one indexed PK lookup per admin request is negligible — and
            // it closes the 15-minute residual-authorization window exactly
            // where the blast radius is largest (suspend, audit, user admin).
            if (isAdminRequest(path)) {
                try {
                    if (!hasCurrentTokenVersion(jwt, userId)) {
                        rejectUnauthorized(response, request.getRequestURI());
                        return;
                    }
                } catch (RuntimeException storeUnavailable) {
                    // Fail closed like the revocation backend: an unreadable
                    // user store must never silently authorize a possibly
                    // demoted admin.
                    log.warn("User store unavailable during admin token-version check");
                    SecurityContextHolder.clearContext();
                    reject(response, HttpStatus.SERVICE_UNAVAILABLE,
                            ErrorCode.SECURITY_BACKEND_UNAVAILABLE.code(),
                            "Security service temporarily unavailable. Please try again later.",
                            request.getRequestURI());
                    return;
                }
            }
            // K7/D8: CSRF bound to the server-verified user id — a transplanted
            // cookie minted for another user fails even with a matching header.
            // (Legacy 43-char cookies from before the rollout fail closed here;
            // affected browsers re-login once and receive bound tokens.)
            if (!bearerAuth && BrowserSessionCookies.requiresCsrf(request.getMethod())
                    && !csrfBinding.verifyCsrfToken(
                            request.getHeader(BrowserSessionCookies.CSRF_HEADER),
                            cookieValue(request, browserSessionCookies.csrfCookieName()),
                            String.valueOf(userId))) {
                reject(response, HttpStatus.FORBIDDEN,
                        ErrorCode.ACCESS_DENIED.code(), "Invalid browser CSRF token",
                        request.getRequestURI());
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = new SimpleAuthenticatedPrincipal(
                        userId,
                        verified.username(),
                        Collections.singletonList(
                                new SimpleGrantedAuthority(role)));

                // Signature already verified above; establish the security context.
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                // MDC userId for downstream logs (prod JSON layout renders it).
                // Removed in the finally below: Tomcat threads are reused and
                // MDC is a ThreadLocal — leaking it would attribute the next
                // request's logs to this user.
                MDC.put(MDC_USER_KEY, String.valueOf(userId));
            }
        } catch (RevocationStoreUnavailableException e) {
            log.warn("JWT revocation backend unavailable; refusing authenticated request");
            SecurityContextHolder.clearContext();
            reject(response, HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SECURITY_BACKEND_UNAVAILABLE.code(),
                    "Security service temporarily unavailable. Please try again later.",
                    request.getRequestURI());
            return;
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            rejectUnauthorized(response, request.getRequestURI());
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_KEY);
        }
    }

    private void rejectUnauthorized(HttpServletResponse response, String path) throws IOException {
        reject(response, HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, path);
    }

    private void reject(HttpServletResponse response, HttpStatus status,
            String code, String message, String path) throws IOException {
        ProblemDetailFactory.writeProblem(response, objectMapper, status, code, message, path);
    }

    private static boolean isAdminRequest(String path) {
        return ADMIN_PATH_PREFIX.equals(path) || path.startsWith(ADMIN_PATH_PREFIX + "/");
    }

    /**
     * Compares the token's {@code ver} claim against the user's current
     * generation. Pre-versioning tokens present 0 and pre-versioning users
     * persist 0 (V39 backfill), so rolling deploys never lock admins out. A
     * deleted user fails closed (empty lookup rejects).
     */
    private boolean hasCurrentTokenVersion(String jwt, Long userId) {
        final long presented = jwtPort.extractTokenVersion(jwt);
        final Optional<User> user = loadUserPort.findById(userId);
        return user.map(current -> current.getTokenVersion() == presented).orElse(false);
    }

    private static boolean isLogoutRequest(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        return PublicApiPaths.LOGOUT.equals(path)
                || PublicApiPaths.BROWSER_LOGOUT.equals(path);
    }

    private static boolean isPublicLogin(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        // REGISTER is permitAll (see SecurityProperties): a stale/expired
        // session cookie on a shared browser must not 401 a new registration
        // before it reaches the controller.
        return PublicApiPaths.LOGIN.equals(path)
                || PublicApiPaths.BROWSER_LOGIN.equals(path)
                || PublicApiPaths.REGISTER.equals(path);
    }

    private static boolean isRefreshRequest(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        return PublicApiPaths.REFRESH.equals(path)
                || PublicApiPaths.BROWSER_REFRESH.equals(path);
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            String servletPath = request.getServletPath();
            return servletPath == null ? "" : servletPath;
        }
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
