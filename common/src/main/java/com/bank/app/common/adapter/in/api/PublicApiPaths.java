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

    public static final String ACCOUNTS = "/api/v1/accounts";
    public static final String TRANSFERS = "/api/v1/transfers";

    public static final String ADMIN = "/api/v1/admin";

    private PublicApiPaths() {}
}
