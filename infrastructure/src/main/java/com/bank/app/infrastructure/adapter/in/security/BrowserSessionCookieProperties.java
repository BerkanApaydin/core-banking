package com.bank.app.infrastructure.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Infrastructure-owned view of the browser-session cookie flag. The User
 * bounded context owns {@code BrowserSessionProperties} for the same key
 * (its controller cannot depend on infrastructure types); this record is the
 * infrastructure side of that deliberate duality — same key, two
 * module-owned views, each bound independently.
 */
@ConfigurationProperties(prefix = "app.security.browser-session")
public record BrowserSessionCookieProperties(
        @DefaultValue("false") boolean secure
) {
}
