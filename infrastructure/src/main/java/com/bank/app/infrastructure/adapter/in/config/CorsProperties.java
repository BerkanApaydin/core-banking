package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:8080"}) List<String> allowedOrigins
) {}
