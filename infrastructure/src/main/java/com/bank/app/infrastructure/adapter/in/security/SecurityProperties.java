package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.api.PublicApiPaths;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> whitelistPaths
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
    }
}
