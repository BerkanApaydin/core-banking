package com.bank.app.audit.domain;

/**
 * Programmer error: an audit action string with no enum mapping.
 *
 * <p>Extends {@link IllegalStateException} (not {@link IllegalArgumentException})
 * on purpose: an unknown action name is always a server-side wiring mistake,
 * never a client error. It therefore falls through to the generic 500 handler
 * instead of the 400 invalid-argument handler, and — because audit writes run
 * inside the business transaction — fails the operation loudly rather than
 * persisting a transfer without its audit trail.
 */
public class UnknownAuditActionException extends IllegalStateException {

    public UnknownAuditActionException(String value) {
        super("Unknown audit action: " + value);
    }
}
