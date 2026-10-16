package com.bank.app.infrastructure.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * SE-4: global JSON body-size cap.
 *
 * <p>Tomcat's {@code max-swallow-size} / {@code max-http-form-post-size} only
 * constrain form posts — a JSON body is streamed straight to Jackson with no
 * container limit, so rate limiting (request <em>count</em>) alone cannot stop
 * a memory-DoS via huge payloads. This single property is the size gate for
 * all mutating requests (see {@link RequestBodySizeLimitFilter}); field-level
 * {@code @Size} constraints remain the second layer.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.request-body")
public record RequestBodyLimitProperties(
        @DefaultValue("2097152") long maxBytes
) {
    public RequestBodyLimitProperties {
        if (maxBytes < 1024) {
            throw new IllegalArgumentException(
                    "Request body limit must be at least 1KB");
        }
    }
}
