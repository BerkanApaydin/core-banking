package com.bank.app.user.application.port.out;

/**
 * Resolves the client IP from already-extracted HTTP values.
 *
 * <p>The port intentionally takes plain {@link String} values instead of
 * {@code HttpServletRequest}: the application boundary must not depend on the
 * servlet API. Extracting headers stays in the inbound adapters
 * (controllers, aspects), IP selection logic stays in the outbound adapter.
 */
public interface ClientIpResolverPort {
    String resolveClientIp(String forwardedForHeader, String remoteAddr);
}
