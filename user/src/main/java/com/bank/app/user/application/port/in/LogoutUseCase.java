package com.bank.app.user.application.port.in;

public interface LogoutUseCase {
    void execute(String authHeader);

    /**
     * Revokes the access token and, when provided, the refresh-token session.
     * API clients that never received (or already discarded) a refresh token
     * use the single-argument form.
     */
    void execute(String authHeader, String refreshToken);
}
