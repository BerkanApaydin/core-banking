package com.bank.app.common.domain.exception;

public class AuthorizationException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.ACCESS_DENIED; }

    /**
     * Matches the wire code. This exception is handled by the dedicated
     * authorization handler (not the generic business handler), which emits
     * {@code ACCESS_DENIED} for every instance regardless of message key
     * (e.g. demo-funding vs not-logged-in) — the message key still selects the
     * human-readable text. Declared here so the code no longer depends on the
     * class-name derivation fallback.
     */
    @Override
    public String getErrorCode() { return "ACCESS_DENIED"; }

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
