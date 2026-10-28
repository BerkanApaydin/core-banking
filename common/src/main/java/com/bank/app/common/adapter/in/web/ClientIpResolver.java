package com.bank.app.common.adapter.in.web;

/**
 * Resolves the client IP from already-extracted HTTP values.
 *
 * <p>Framework-free shared-kernel web contract: web filters, aspects and
 * controllers use it to translate transport-level proxy headers into the
 * client address consumed by rate limiting, brute-force lockout and
 * public-endpoint idempotency keys. It intentionally takes plain {@link String}
 * values instead of {@code HttpServletRequest}: the contract stays servlet-API
 * free while header extraction remains in the inbound adapter.
 */
public interface ClientIpResolver {
    String resolveClientIp(String forwardedForHeader, String remoteAddr);
}
