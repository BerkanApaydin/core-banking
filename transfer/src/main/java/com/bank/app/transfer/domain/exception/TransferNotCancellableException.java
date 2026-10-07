package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

/**
 * Deliberately no {@code getErrorCode} override: the code is instance-variant
 * by design ({@code TRANSFER_NOT_CANCELLABLE} vs
 * {@code TRANSFER_CANCELLATION_WINDOW_EXPIRED}, see {@code Transfer.cancel}).
 * A per-class literal would collapse two distinct client-visible failures.
 */
public class TransferNotCancellableException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public TransferNotCancellableException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
