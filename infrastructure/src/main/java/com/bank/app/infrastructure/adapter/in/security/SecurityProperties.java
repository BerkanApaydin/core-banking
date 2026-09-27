package com.bank.app.infrastructure.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> whitelistPaths
) {
    public SecurityProperties {
        if (whitelistPaths == null || whitelistPaths.isEmpty()) {
            whitelistPaths = List.of(
                "/api/v1/auth/login", "/api/v1/auth/browser/login", "/api/v1/auth/register",
                "/v3/api-docs/**", "/swagger-ui/**",
                "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                "/app.js", "/boot.js", "/style.css", "/favicon.ico", "/error"
            );
        }
    }
}
