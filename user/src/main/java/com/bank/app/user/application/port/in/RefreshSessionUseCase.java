package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthResponse;

public interface RefreshSessionUseCase {

    /**
     * Rotates a refresh token into a fresh access + refresh pair.
     * Re-presenting an already-rotated token revokes its whole family.
     */
    AuthResponse execute(String refreshToken);

    /**
     * Browser-session rotation. The refresh token is verified exactly once and
     * the CSRF pair is validated against that verified user id before any
     * rotation state changes. Missing or mismatched tokens fail closed.
     */
    AuthResponse executeWithCsrf(String refreshToken, String csrfHeader, String csrfCookie);
}
