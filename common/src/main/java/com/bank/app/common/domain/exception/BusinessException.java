package com.bank.app.common.domain.exception;

import java.util.Locale;

public abstract class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final Locale LOCALE = Locale.ENGLISH;
    private final String messageKey;
    private final transient Object[] args;

    protected BusinessException(String message) {
        super(message);
        this.messageKey = null;
        this.args = null;
    }

    protected BusinessException(String messageKey, Object[] args, String defaultMessage) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args;
    }

    protected BusinessException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(defaultMessage, cause);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }

    /** Describes the failure without imposing a transport-specific response. */
    public BusinessFailureKind getFailureKind() {
        return BusinessFailureKind.RULE_VIOLATION;
    }

    /**
     * Wire-stable error code. Concrete exceptions SHOULD declare their own
     * literal (enforced by {@code ExceptionCodeArchitectureTest}); the
     * derivation below is the legacy fallback for the few instance-variant
     * codes whose value legitimately differs per throw site
     * (e.g. {@code ConcurrentRequestException} reports payload-conflict vs
     * key-required, {@code TransferNotCancellableException} reports
     * not-cancellable vs window-expired). Never rely on the class-name branch
     * for new exceptions — declare the literal.
     */
    public String getErrorCode() {
        if (messageKey != null) {
            return messageKey.replace("error.", "").toUpperCase(LOCALE);
        }
        String simpleName = getClass().getSimpleName();
        return simpleName
                .replaceAll("Exception$", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(LOCALE);
    }
}
