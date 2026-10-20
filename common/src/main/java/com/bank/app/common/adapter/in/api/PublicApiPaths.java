package com.bank.app.common.adapter.in.api;

/**
 * Single source of truth for public (permitAll) auth paths and rate-limited
 * API prefixes.
 *
 * <p>These strings are matched in three independent places — the security
 * whitelist ({@code SecurityProperties}), the rate-limit filter
 * ({@code RateLimitProperties}) and the login bypass in
 * {@code JwtAuthenticationFilter}. A rename in only one of them silently
 * breaks login or opens a bypass, so every usage must reference these
 * constants instead of duplicating literals.
 */
public final class PublicApiPaths {

    /** Shared prefix of every authentication endpoint — the tight rate-limit tier. */
    public static final String AUTH_PREFIX = "/api/v1/auth";

    public static final String LOGIN = "/api/v1/auth/login";
    public static final String BROWSER_LOGIN = "/api/v1/auth/browser/login";
    public static final String REGISTER = "/api/v1/auth/register";
    public static final String REFRESH = "/api/v1/auth/refresh";
    public static final String BROWSER_REFRESH = "/api/v1/auth/browser/refresh";
    public static final String LOGOUT = "/api/v1/auth/logout";
    public static final String BROWSER_LOGOUT = "/api/v1/auth/browser/logout";
    public static final String BROWSER_SESSION = "/api/v1/auth/browser/session";
    // Audit (password reset): unauthenticated by necessity — users without a
    // session reset passwords. Abuse-contained by the auth-tier rate budget
    // (AUTH_PREFIX), enumeration-safe generic responses and audit.
    public static final String PASSWORD_RESET_REQUEST = "/api/v1/auth/password/reset/request";
    public static final String PASSWORD_RESET_CONFIRM = "/api/v1/auth/password/reset/confirm";

    /**
     * MFA management endpoints (enrollment, status, removal). They live
     * under the AUTH prefix on purpose: the MFA code check is a
     * brute-force target, so these paths must inherit the tight auth-tier
     * rate-limit budget instead of the looser resource budget. The
     * per-account attempt counter ({@code MfaAttemptPort}) adds the second
     * layer that the IP-keyed budget cannot provide.
     */
    public static final String MFA = "/api/v1/auth/mfa";

    public static final String ACCOUNTS = "/api/v1/accounts";
    public static final String TRANSFERS = "/api/v1/transfers";

    public static final String ADMIN = "/api/v1/admin";

    private PublicApiPaths() {}
}
