package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthResponse;

public interface RefreshSessionUseCase {

    /**
     * Rotates a refresh token into a fresh access + refresh pair.
     * Re-presenting an already-rotated token revokes its whole family.
     */
    AuthResponse execute(String refreshToken);
}
