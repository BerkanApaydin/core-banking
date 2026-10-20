package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
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

    private final ObjectMapper objectMapper;
    private final BrowserSessionCookies browserSessionCookies;
    private final JwtRequestAuthenticator authenticator;

    @Autowired
    public JwtAuthenticationFilter(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort, LoadUserPort loadUserPort,
            ObjectMapper objectMapper,
            CsrfBindingPort csrfBinding,
            BrowserSessionCookieProperties browserSession) {
        this.objectMapper = objectMapper;
        this.browserSessionCookies = new BrowserSessionCookies(browserSession.secure());
        this.authenticator = new JwtRequestAuthenticator(jwtPort, tokenBlacklistPort,
                loadUserPort, csrfBinding, browserSessionCookies);
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
        final String path = FilterRequestDecisions.requestPath(request);
        // Refresh endpoints own their authentication entirely (refresh token
        // in body or cookie, validated by the controller): an expired or
        // blacklisted access token in the header/cookie must not block a
        // legitimate rotation, so the filter steps aside completely here.
        if (FilterRequestDecisions.isRefreshRequest(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String jwt = extractJwt(request, path, authHeader);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (jwt.isBlank()) {
            rejectUnauthorized(response, request.getRequestURI());
            return;
        }

        final boolean bearerAuth = authHeader != null && authHeader.startsWith(BEARER_PREFIX);
        try {
            JwtRequestAuthenticator.Result result = authenticator.authenticate(
                    request, path, jwt, bearerAuth);
            switch (result) {
                case PASS_THROUGH:
                    filterChain.doFilter(request, response);
                    return;
                case UNAUTHORIZED:
                    rejectUnauthorized(response, request.getRequestURI());
                    return;
                case FORBIDDEN:
                    reject(response, HttpStatus.FORBIDDEN,
                            ErrorCode.ACCESS_DENIED.code(), "Invalid browser CSRF token",
                            request.getRequestURI());
                    return;
                case BACKEND_UNAVAILABLE:
                    SecurityContextHolder.clearContext();
                    rejectServiceUnavailable(response, request.getRequestURI());
                    return;
                case AUTHENTICATED:
                    break;
            }
        } catch (RevocationStoreUnavailableException e) {
            log.warn("JWT revocation backend unavailable; refusing authenticated request");
            SecurityContextHolder.clearContext();
            rejectServiceUnavailable(response, request.getRequestURI());
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

    /**
     * Selects the credential for this request: Bearer header wins; otherwise the
     * browser session cookie authenticates API calls (but never the public shell
     * or login/refresh endpoints, so an expired cookie cannot lock the login UI).
     */
    private String extractJwt(HttpServletRequest request, String path, String authHeader) {
        final boolean bearerAuth = authHeader != null && authHeader.startsWith(BEARER_PREFIX);
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
        if (bearerAuth) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        if (browserAuth) {
            return FilterRequestDecisions.cookieValue(request, browserSessionCookies.sessionCookieName());
        }
        return null;
    }

    private void rejectUnauthorized(HttpServletResponse response, String path) throws IOException {
        reject(response, HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, path);
    }

    private void rejectServiceUnavailable(HttpServletResponse response, String path) throws IOException {
        reject(response, HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SECURITY_BACKEND_UNAVAILABLE.code(),
                "Security service temporarily unavailable. Please try again later.",
                path);
    }

    private void reject(HttpServletResponse response, HttpStatus status,
            String code, String message, String path) throws IOException {
        ProblemDetailFactory.writeProblem(response, objectMapper, status, code, message, path);
    }
}
