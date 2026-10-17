package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.api.PublicApiPaths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> whitelistPaths,
    // Audit: BCrypt cost factor was a magic literal (12) in SecurityConfig.
    // Bound here so ops can raise it on faster hardware without a rebuild;
    // existing hashes keep verifying (cost is embedded in the hash).
    @DefaultValue("12") int passwordEncoderStrength,
    // Audit: HSTS max-age was a magic literal (31536000 = 365 days) in
    // SecurityConfig. One year is the preload-list minimum; lowering it
    // weakens downgrade protection, so the validator rejects negatives.
    @DefaultValue("31536000") long hstsMaxAgeSeconds
) {
    /**
     * Default public paths, used ONLY when {@code app.security.whitelist-paths}
     * is unset (null). An explicitly empty list means "nothing is public" and
     * stays empty (fail-closed) — see K2/D2.
     */
    public static final List<String> DEFAULT_WHITELIST = List.of(
        PublicApiPaths.LOGIN, PublicApiPaths.BROWSER_LOGIN, PublicApiPaths.REGISTER,
        // Refresh endpoints authenticate with the refresh token itself
        // (like login with a password), so they never require a session.
        PublicApiPaths.REFRESH, PublicApiPaths.BROWSER_REFRESH,
        // Password-reset endpoints are unauthenticated by necessity (audit);
        // abuse-contained by the auth-tier rate budget + generic responses.
        PublicApiPaths.PASSWORD_RESET_REQUEST, PublicApiPaths.PASSWORD_RESET_CONFIRM,
        "/v3/api-docs/**", "/swagger-ui/**",
        "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
        // Static UI assets as patterns (not an enumerated file list): a new
        // frontend file must not silently break the login UI (see 6.3).
        // Root-level bundles served from static/ plus any future /assets/**.
        "/*.js", "/*.css", "/assets/**",
        "/style.css", "/favicon.ico", "/error"
    );

    public SecurityProperties {
        if (whitelistPaths == null) {
            whitelistPaths = DEFAULT_WHITELIST;
        }
        // Empty list = operator explicitly closed all public paths.
        // Fail-closed: do NOT fall back to defaults (K2/D2).
        if (passwordEncoderStrength < 4 || passwordEncoderStrength > 31) {
            throw new IllegalArgumentException(
                    "BCrypt strength must be in [4, 31] (log2 rounds)");
        }
        if (hstsMaxAgeSeconds < 0) {
            throw new IllegalArgumentException("HSTS max-age must not be negative");
        }
    }
}
