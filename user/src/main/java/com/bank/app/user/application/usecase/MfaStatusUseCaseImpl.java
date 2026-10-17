package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.exception.UserNotFoundException;
import java.util.Objects;

/**
 * Read-only MFA status for the UI. Unknown users fail like any other
 * authenticated read (404) — the caller is already authenticated, so there
 * is no enumeration surface beyond the caller's own account.
 */
@ReadOnlyUseCase
public class MfaStatusUseCaseImpl implements MfaStatusUseCase {

    private final LoadUserPort loadUserPort;

    public MfaStatusUseCaseImpl(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    public boolean isEnabled(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId))
                .isMfaEnabled();
    }
}
