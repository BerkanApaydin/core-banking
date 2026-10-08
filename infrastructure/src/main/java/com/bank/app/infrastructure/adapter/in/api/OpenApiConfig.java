package com.bank.app.infrastructure.adapter.in.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Central OpenAPI metadata.
 *
 * <p>Time contract (docs/decisions/time-strategy.md): every datetime in this
 * API is UTC, rendered as offset-less ISO-8601 ({@code 2026-05-01T12:00:00}).
 * Clients must interpret naive timestamps as UTC and must not attach a local
 * offset; servers never emit one.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Core Banking & Transfer API",
        version = "v1",
        description = "All datetimes are UTC, offset-less ISO-8601 "
                + "(e.g. 2026-05-01T12:00:00 means 12:00 UTC). "
                + "See docs/decisions/time-strategy.md."))
public class OpenApiConfig {
}
