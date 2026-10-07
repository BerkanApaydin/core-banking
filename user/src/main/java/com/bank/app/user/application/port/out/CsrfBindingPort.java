package com.bank.app.user.application.port.out;

/**
 * Session-bound CSRF token mint/verification (K7/D8), owned by the User
 * bounded context (browser session lives here) and implemented by
 * infrastructure. The binding subject is the server-verified user id: stable
 * across access/refresh rotation (unlike either token), unguessable-enough
 * combined with the server-side MAC key, and immune to cross-user cookie
 * transplants. Callers must pass a verified identity — never a client claim.
 */
public interface CsrfBindingPort {

    /** Mints a token bound to {@code bindingSubject}; never null/blank input. */
    String issueCsrfToken(String bindingSubject);

    /**
     * Double-submit equality plus MAC verification against
     * {@code bindingSubject}. Null/blank inputs fail closed (false).
     */
    boolean verifyCsrfToken(String header, String cookie, String bindingSubject);
}
