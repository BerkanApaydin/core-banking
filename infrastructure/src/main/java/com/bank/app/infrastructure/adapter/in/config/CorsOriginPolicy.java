package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.core.env.Environment;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Production CORS-origin policy: no loopback origins in prod.
 */
final class CorsOriginPolicy {

    private CorsOriginPolicy() {
    }

    static void requireProdCors(Environment environment) {
        // Base application.yml ships localhost origins for local development and
        // application-prod.yml inherits them. A prod deployment that forgets
        // CORS_ALLOWED_ORIGINS would otherwise accept cross-origin credentialed
        // calls from any localhost page (e.g. a malicious site opened in the
        // same browser as the banking UI). Fail fast instead of serving them.
        // L-1: parse per-origin hosts instead of substring-scanning the whole
        // list — a substring rejects innocent hosts like my-localhost-app.com
        // and cannot distinguish hosts from paths.
        String origins = environment.getProperty("app.security.cors.allowed-origins", "");
        if (origins == null || origins.isBlank()) {
            return;
        }
        for (String raw : origins.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            String host = originHost(origin);
            if (isLoopbackHost(host)) {
                throw new IllegalStateException(
                        "Production must not allow localhost CORS origins (found: " + origin + "). "
                        + "Set CORS_ALLOWED_ORIGINS to explicit deployment origins.");
            }
        }
    }

    static String originHost(String origin) {
        try {
            URI uri = new URI(origin);
            String host = uri.getHost();
            if (host != null) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (URISyntaxException ignored) {
            // Fall through to the raw check below.
        }
        return origin.toLowerCase(Locale.ROOT);
    }

    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1")) {
            return true;
        }
        // Subdomains of localhost (foo.localhost) resolve loopback as well.
        return h.equals(".localhost") || h.endsWith(".localhost");
    }
}
