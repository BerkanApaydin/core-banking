package com.bank.app.common.application.exception;

/**
 * Raised by inbound adapters when an endpoint requires an authenticated
 * principal but none is present in the security context.
 *
 * <p>Framework-free on purpose: web adapters must not depend on domain
 * exception packages, and infrastructure maps this type to the same RFC 7807
 * 401 body as the rest of the security failures.
 */
public class AuthenticationRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationRequiredException() {
        super("Authentication is required.");
    }
}
