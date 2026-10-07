package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

/**
 * A rotated (already replaced) refresh token was presented again: probable
 * token theft. The whole token family is revoked as the response; the client
 * must re-authenticate with credentials.
 */
public class RefreshTokenReuseException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.AUTHENTICATION_FAILED; }

    @Override
    public String getErrorCode() { return "REFRESH_TOKEN_REUSE_DETECTED"; }

    public RefreshTokenReuseException() {
        super("error.refresh_token_reuse_detected", null,
                "Refresh token reuse detected. All sessions have been revoked; please log in again.");
    }
}
