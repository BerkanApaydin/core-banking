package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token-verification half of {@link JwtAuthenticationFilter}: signature,
 * revocation, token type, admin token generation, CSRF, and context
 * establishment.
 *
 * <p>
 * Returns an outcome instead of writing HTTP responses so the filter owns all
 * status/code/message decisions in one place. Authentication-store failures
 * that must fail closed surface as {@link Result#BACKEND_UNAVAILABLE}.
 */
final class JwtRequestAuthenticator {

    enum Result {
        AUTHENTICATED,
        PASS_THROUGH,
        UNAUTHORIZED,
        FORBIDDEN,
        MFA_REQUIRED,
        BACKEND_UNAVAILABLE
    }

    private static final Logger log = LoggerFactory.getLogger(JwtRequestAuthenticator.class);

    private final JwtPort jwtPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final CsrfBindingPort csrfBinding;
    private final BrowserSessionCookies browserSessionCookies;
    private final AdminTokenVersionValidator tokenVersionValidator;
    private final AdminMfaValidator adminMfaValidator;

    JwtRequestAuthenticator(JwtPort jwtPort,
            TokenBlacklistPort tokenBlacklistPort,
            LoadUserPort loadUserPort,
            CsrfBindingPort csrfBinding,
            BrowserSessionCookies browserSessionCookies) {
        this.jwtPort = jwtPort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.csrfBinding = csrfBinding;
        this.browserSessionCookies = browserSessionCookies;
        this.tokenVersionValidator = new AdminTokenVersionValidator(jwtPort, loadUserPort);
        this.adminMfaValidator = new AdminMfaValidator(loadUserPort);
    }

    Result authenticate(HttpServletRequest request, String path, String jwt, boolean bearerAuth) {
        // Validate the signature FIRST so unauthenticated callers cannot use
        // this endpoint as a blacklist oracle or force Redis/Caffeine lookups
        // with arbitrary strings (DoS surface).
        JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(jwt);
        if (verified == null) {
            // Logout with an EXPIRED (or otherwise unverifiable) access token
            // must still reach the controller: rejecting it here meant the
            // user could not log out at all. A logout is a termination
            // request, not an authorization decision — what it revokes are
            // the very credentials the caller presents. CSRF is skipped on
            // this path because the binding subject comes from the token that
            // could not be verified; the session cookie is SameSite=Strict,
            // so a cross-site request carries nothing to revoke.
            return FilterRequestDecisions.isLogoutRequest(request, path)
                    ? Result.PASS_THROUGH
                    : Result.UNAUTHORIZED;
        }

        if (isBlacklisted(request, path, jwt)) {
            return Result.UNAUTHORIZED;
        }
        if (isRefreshTokenMisused(request, path, jwt)) {
            return Result.UNAUTHORIZED;
        }

        Long userId = verified.userId();
        String role = verified.role();
        // G-2 accepted trade-off (stateless JWT): role and userId come from
        // the verified access token without a per-request DB lookup. A role
        // demotion therefore takes effect on non-admin paths only after the
        // access token expires (default 15 min); admin paths re-validate the
        // token generation below (SEC-01).
        if (userId == null || role == null) {
            // Stateless JWT requires userId+role claims; legacy tokens without
            // claims are rejected instead of falling back to a DB lookup.
            return Result.UNAUTHORIZED;
        }
        if (FilterRequestDecisions.isAdminRequest(path)) {
            Result adminCheck = verifyAdminAccess(jwt, userId);
            if (adminCheck != Result.AUTHENTICATED) {
                return adminCheck;
            }
        }
        if (!bearerAuth && isCsrfInvalid(request, userId)) {
            return Result.FORBIDDEN;
        }

        SecurityContextPopulator.establish(verified, role, request);
        return Result.AUTHENTICATED;
    }

    /**
     * A previously successful logout may lose its HTTP response. Let only this
     * exact action re-submit a still-valid signed token so revocation can be
     * retried idempotently; every other route must continue enforcing the
     * blacklist.
     */
    private boolean isBlacklisted(HttpServletRequest request, String path, String jwt) {
        return !FilterRequestDecisions.isLogoutRequest(request, path)
                && tokenBlacklistPort.isBlacklisted(jwt);
    }

    /**
     * Refresh tokens authenticate only the refresh endpoints: a leaked
     * long-lived token must never pass as API authorization.
     */
    private boolean isRefreshTokenMisused(HttpServletRequest request, String path, String jwt) {
        return JwtPort.TOKEN_TYPE_REFRESH.equals(jwtPort.extractTokenType(jwt))
                && !FilterRequestDecisions.isRefreshRequest(request, path);
    }

    /**
     * Admin paths re-validate the token generation against the user store
     * (SEC-01), closing the G-2 demotion window where it matters most, and
     * require an enrolled second factor. An unreadable store fails closed,
     * never silently authorizing a possibly demoted or password-only admin.
     */
    private Result verifyAdminAccess(String jwt, Long userId) {
        try {
            if (!tokenVersionValidator.hasCurrentTokenVersion(jwt, userId)) {
                return Result.UNAUTHORIZED;
            }
            return adminMfaValidator.isMfaEnabled(userId)
                    ? Result.AUTHENTICATED
                    : Result.MFA_REQUIRED;
        } catch (RuntimeException storeUnavailable) {
            log.warn("User store unavailable during admin security re-check");
            return Result.BACKEND_UNAVAILABLE;
        }
    }

    /**
     * K7/D8: CSRF bound to the server-verified user id — a transplanted cookie
     * minted for another user fails even with a matching header. (Legacy 43-char
     * cookies from before the rollout fail closed here; affected browsers
     * re-login once and receive bound tokens.)
     */
    private boolean isCsrfInvalid(HttpServletRequest request, Long userId) {
        return BrowserSessionCookies.requiresCsrf(request.getMethod())
                && !csrfBinding.verifyCsrfToken(
                        request.getHeader(BrowserSessionCookies.CSRF_HEADER),
                        FilterRequestDecisions.cookieValue(request, browserSessionCookies.csrfCookieName()),
                        String.valueOf(userId));
    }
}
