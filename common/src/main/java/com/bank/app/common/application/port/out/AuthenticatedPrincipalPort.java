package com.bank.app.common.application.port.out;

/**
 * Framework-free view of an authenticated principal.
 *
 * <p>Implemented by Spring Security {@code UserDetails} types wherever they live
 * (user adapters, infrastructure adapters). Consumers such as
 * {@code SecurityContextAdapter} program against this interface so infrastructure
 * never depends on a bounded context's concrete adapter classes (DIP).
 */
public interface AuthenticatedPrincipalPort {

    Long getAuthenticatedUserId();

    String getAuthenticatedUsername();
}
