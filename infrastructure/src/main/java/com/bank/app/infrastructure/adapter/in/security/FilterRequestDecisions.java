package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.api.PublicApiPaths;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Pure request-classification predicates for the JWT filter chain.
 *
 * <p>Extracted from {@code JwtAuthenticationFilter} so routing decisions
 * (refresh bypass, public-login cookie skip, admin re-validation scope) are
 * readable and unit-testable without servlet scaffolding. No I/O, no state.
 */
final class FilterRequestDecisions {

    // SEC-01: mirrors SecurityConfig's "/api/v1/admin/**" matcher (G-1 outer
    // layer). Requests under this prefix re-validate the token generation
    // against the DB (see AdminTokenVersionValidator).
    static final String ADMIN_PATH_PREFIX = "/api/v1/admin";
    // L-5: runtime log-level changes (/actuator/loggers, ADMIN-only per
    // SecurityConfig) get the same re-validation — a demoted admin must not
    // keep log control for ~15 min on a stale token.
    static final String ACTUATOR_LOGGERS_PREFIX = "/actuator/loggers";

    private FilterRequestDecisions() {
    }

    static boolean isAdminRequest(String path) {
        return ADMIN_PATH_PREFIX.equals(path) || path.startsWith(ADMIN_PATH_PREFIX + "/")
                || ACTUATOR_LOGGERS_PREFIX.equals(path) || path.startsWith(ACTUATOR_LOGGERS_PREFIX + "/");
    }

    static boolean isLogoutRequest(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        return PublicApiPaths.LOGOUT.equals(path)
                || PublicApiPaths.BROWSER_LOGOUT.equals(path);
    }

    static boolean isPublicLogin(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        // REGISTER is permitAll (see SecurityProperties): a stale/expired
        // session cookie on a shared browser must not 401 a new registration
        // before it reaches the controller.
        return PublicApiPaths.LOGIN.equals(path)
                || PublicApiPaths.BROWSER_LOGIN.equals(path)
                || PublicApiPaths.REGISTER.equals(path);
    }

    static boolean isRefreshRequest(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) return false;
        return PublicApiPaths.REFRESH.equals(path)
                || PublicApiPaths.BROWSER_REFRESH.equals(path);
    }

    static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            String servletPath = request.getServletPath();
            return servletPath == null ? "" : servletPath;
        }
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

    static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
