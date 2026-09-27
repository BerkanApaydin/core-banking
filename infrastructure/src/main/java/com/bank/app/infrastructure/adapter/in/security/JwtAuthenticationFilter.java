package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.SimpleAuthenticatedPrincipal;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String MDC_USER_KEY = "userId";
    // Single generic message for revoked AND invalid tokens: distinct messages
    // let callers oracle whether a token was revoked (information disclosure).
    private static final String MSG_TOKEN_INVALID = "Invalid or expired token";

    private final JwtPort jwtPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final ObjectMapper objectMapper;
    private final BrowserSessionCookies browserSessionCookies;

    @Autowired
    public JwtAuthenticationFilter(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort, ObjectMapper objectMapper,
            @Value("${app.security.browser-session.secure:false}") boolean secureCookies) {
        this.jwtPort = jwtPort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.objectMapper = objectMapper;
        this.browserSessionCookies = new BrowserSessionCookies(secureCookies);
    }

    // Kept for isolated filter tests and non-Spring construction.
    public JwtAuthenticationFilter(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort, ObjectMapper objectMapper) {
        this(jwtPort, tokenBlacklistPort, objectMapper, false);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        final boolean bearerAuth = authHeader != null && authHeader.startsWith(BEARER_PREFIX);
        final String path = requestPath(request);
        // The browser session cookie is for API calls. Browsers also send it
        // with / and static assets; an expired cookie must not prevent the
        // public application shell from loading so it can show the login UI.
        final boolean browserAuth = authHeader == null
                && path.startsWith("/api/")
                && !isPublicLogin(request, path);
        final String jwt = bearerAuth ? authHeader.substring(BEARER_PREFIX.length())
                : browserAuth ? cookieValue(request, browserSessionCookies.sessionCookieName()) : null;

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (jwt.isBlank()) {
            ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, request.getRequestURI());
            return;
        }

        try {
            // Validate the signature FIRST so unauthenticated callers cannot use
            // this endpoint as a blacklist oracle or force Redis/Caffeine lookups
            // with arbitrary strings (DoS surface).
            JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(jwt);
            if (verified == null) {
                ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, request.getRequestURI());
                return;
            }
            // A previously successful logout may lose its HTTP response. Let
            // only this exact action re-submit a still-valid signed token so
            // revocation can be retried idempotently; every other route must
            // continue enforcing the blacklist.
            boolean logoutRequest = "POST".equals(request.getMethod())
                    && ("/api/v1/auth/logout".equals(path)
                    || "/api/v1/auth/browser/logout".equals(path));
            if (!logoutRequest && tokenBlacklistPort.isBlacklisted(jwt)) {
                ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, request.getRequestURI());
                return;
            }
            if (!bearerAuth && requiresCsrf(request)
                    && !validCsrf(request, browserSessionCookies.csrfCookieName())) {
                ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.FORBIDDEN,
                        ErrorCode.ACCESS_DENIED.code(), "Invalid browser CSRF token", request.getRequestURI());
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = verified.userId();
                String role = verified.role();
                if (userId == null || role == null) {
                    // Stateless JWT requires userId+role claims; legacy tokens without
                    // claims are rejected instead of falling back to a DB lookup.
                    // Clients must re-login to obtain a current token.
                    ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                            ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, request.getRequestURI());
                    return;
                }
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
            ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SECURITY_BACKEND_UNAVAILABLE.code(),
                    "Security service temporarily unavailable. Please try again later.", request.getRequestURI());
            return;
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTHENTICATION_FAILED.code(), MSG_TOKEN_INVALID, request.getRequestURI());
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_KEY);
        }
    }

    private static boolean isPublicLogin(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        return PublicApiPaths.LOGIN.equals(path)
                || PublicApiPaths.BROWSER_LOGIN.equals(path);
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

    private static boolean requiresCsrf(HttpServletRequest request) {
        return !switch (request.getMethod()) {
            case "GET", "HEAD", "OPTIONS", "TRACE" -> true;
            default -> false;
        };
    }

    private static boolean validCsrf(HttpServletRequest request, String cookieName) {
        String header = request.getHeader(BrowserSessionCookies.CSRF_HEADER);
        String cookie = cookieValue(request, cookieName);
        if (header == null || cookie == null || header.length() != 43 || cookie.length() != 43) {
            return false;
        }
        return MessageDigest.isEqual(header.getBytes(StandardCharsets.US_ASCII),
                cookie.getBytes(StandardCharsets.US_ASCII));
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
