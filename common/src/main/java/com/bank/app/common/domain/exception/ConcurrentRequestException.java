package com.bank.app.common.domain.exception;

public class ConcurrentRequestException extends BusinessException {
    private static final long serialVersionUID = 1L;

    // Deliberately no getErrorCode override: the code is instance-variant by
    // design (CONCURRENT_REQUEST vs IDEMPOTENCY_PAYLOAD_CONFLICT vs
    // IDEMPOTENCY_KEY_REQUIRED) — a per-class literal would collapse distinct
    // client-visible failures.

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.CONFLICT; }

    public ConcurrentRequestException(String message) {
        super(message);
    }

    public ConcurrentRequestException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
