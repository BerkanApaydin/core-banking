package com.bank.app.user.application.dto;

public record AuthResponse(
    String token,
    String refreshToken,
    Long userId,
    String username,
    long expiresInMs
) {
}
