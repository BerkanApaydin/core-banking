package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import java.util.List;
import java.util.Objects;

/**
 * Password policy violation surfaced as a business failure (not a bare
 * {@link IllegalArgumentException}) so the violated rules reach the client
 * through the i18n message instead of being replaced by a generic
 * "Invalid request argument." response.
 */
public class WeakPasswordException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RULE_VIOLATION; }

    @Override
    public String getErrorCode() { return "WEAK_PASSWORD"; }

    public WeakPasswordException(List<String> violations) {
        super("error.weak_password", new Object[]{join(violations)}, join(violations));
    }

    private static String join(List<String> violations) {
        Objects.requireNonNull(violations, "Violations must not be null");
        return String.join("; ", violations);
    }
}
