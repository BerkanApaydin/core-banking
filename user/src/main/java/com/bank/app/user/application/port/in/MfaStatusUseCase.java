package com.bank.app.user.application.port.in;

public interface MfaStatusUseCase {
    /** Returns whether MFA is enabled for the user (drives the UI status row). */
    boolean isEnabled(Long userId);
}
