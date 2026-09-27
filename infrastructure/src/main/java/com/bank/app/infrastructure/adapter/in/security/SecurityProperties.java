package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.adapter.in.api.PublicApiPaths;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> whitelistPaths
) {
    public SecurityProperties {
        if (whitelistPaths == null || whitelistPaths.isEmpty()) {
            whitelistPaths = List.of(
                PublicApiPaths.LOGIN, PublicApiPaths.BROWSER_LOGIN, PublicApiPaths.REGISTER,
                "/v3/api-docs/**", "/swagger-ui/**",
                "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                "/app.js", "/boot.js", "/accounts.js", "/transfers.js",
                "/idempotency.js", "/i18n.js",
                "/style.css", "/favicon.ico", "/error"
            );
        }
    }
}
