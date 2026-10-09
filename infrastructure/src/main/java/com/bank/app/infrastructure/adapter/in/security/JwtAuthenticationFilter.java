package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    // Single generic message for revoked AND invalid tokens: distinct messages
    // let callers oracle whether a token was revoked (information disclosure).
    private static final String MSG_TOKEN_INVALID = "Invalid or expired token";

    private final JwtPort jwtPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final LoadUserPort loadUserPort;
    private final ObjectMapper objectMapper;
    private final BrowserSessionCookies browserSessionCookies;
    private final CsrfBindingPort csrfBinding;
    private final AdminTokenVersionValidator tokenVersionValidator;

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
        this.tokenVersionValidator = new AdminTokenVersionValidator(jwtPort, loadUserPort);
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
        final String path = FilterRequestDecisions.requestPath(request);
        // Refresh endpoints own their authentication entirely (refresh token
        // in body or cookie, validated by the controller): an expired or
        // blacklisted access token in the header/cookie must not block a
        // legitimate rotation, so the filter steps aside completely here.
        if (FilterRequestDecisions.isRefreshRequest(request, path)) {
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
                && !FilterRequestDecisions.isPublicLogin(request, path)
                && !FilterRequestDecisions.isRefreshRequest(request, path);
        final String jwt = bearerAuth ? authHeader.substring(BEARER_PREFIX.length())
                : browserAuth ? FilterRequestDecisions.cookieValue(request, browserSessionCookies.sessionCookieName()) : null;

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
            if (!FilterRequestDecisions.isLogoutRequest(request, path) && tokenBlacklistPort.isBlacklisted(jwt)) {
                rejectUnauthorized(response, request.getRequestURI());
                return;
            }
            // Refresh tokens authenticate only the refresh endpoints: a leaked
            // long-lived token must never pass as API authorization.
            if (JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtPort.extractTokenType(jwt))
                    && !FilterRequestDecisions.isRefreshRequest(request, path)) {
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
            if (FilterRequestDecisions.isAdminRequest(path)) {
                try {
                    if (!tokenVersionValidator.hasCurrentTokenVersion(jwt, userId)) {
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
                            FilterRequestDecisions.cookieValue(request, browserSessionCookies.csrfCookieName()),
                            String.valueOf(userId))) {
                reject(response, HttpStatus.FORBIDDEN,
                        ErrorCode.ACCESS_DENIED.code(), "Invalid browser CSRF token",
                        request.getRequestURI());
                return;
            }
            SecurityContextPopulator.establish(verified, role, request);
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
            SecurityContextPopulator.clear();
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
}
