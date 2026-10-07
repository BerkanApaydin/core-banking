package com.bank.app.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * Single place that turns a servlet request into the application-relative,
 * percent-decoded request path used by every path-matching filter and guard.
 *
 * <p>{@link HttpServletRequest#getRequestURI()} returns the RAW, still
 * percent-encoded URI and INCLUDES the servlet context path. Matching filter
 * prefixes against it has two bypasses:
 * <ul>
 *   <li>Percent-encoding: {@code POST /api/v1/%61ccounts} never
 *   {@code startsWith("/api/v1/accounts")}, yet Spring MVC decodes it and
 *   routes it to the account endpoint — so the request escapes rate limiting
 *   and derives a different idempotency key for the same logical operation.</li>
 *   <li>Context path: under a non-root deployment ({@code /bank/api/...})
 *   no {@code /api/...} prefix ever matches, silently disabling every
 *   prefix-based guard at once.</li>
 * </ul>
 * {@link UrlPathHelper#getPathWithinApplication} strips the context path and
 * decodes, which is exactly the path Spring MVC matches handlers against —
 * so a filter decision can no longer disagree with the handler mapping.
 */
public final class RequestPathResolver {

    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance;

    private RequestPathResolver() {
    }

    /**
     * Application-relative, decoded path (for example
     * {@code /api/v1/transfers}), never {@code null}.
     */
    public static String resolve(HttpServletRequest request) {
        String path = PATH_HELPER.getPathWithinApplication(request);
        return path == null || path.isEmpty() ? "/" : path;
    }

    /**
     * Prefix match that cannot over-match sibling paths:
     * {@code /api/v1/accounts} matches {@code /api/v1/accounts/123} but not
     * {@code /api/v1/accountsextra}.
     */
    public static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
