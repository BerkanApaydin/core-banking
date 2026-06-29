package com.bank.app.common.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> whitelistPaths
) {
    public SecurityProperties {
        if (whitelistPaths == null || whitelistPaths.isEmpty()) {
            whitelistPaths = List.of(
                "/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**",
                "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                "/app.js", "/style.css", "/favicon.ico", "/error"
            );
        }
    }
}
