package com.bank.app.common.application.port.out.security;

public interface JwtPort {
    String extractUsername(String token);
    default String generateToken(Long userId, String username) {
        return generateToken(userId, username, "ROLE_USER");
    }
    String generateToken(Long userId, String username, String role);
    String extractRole(String token);
    Long extractUserId(String token);
    boolean isTokenValid(String token);

    default long getExpirationMs() {
        return 86400000L;
    }
}
