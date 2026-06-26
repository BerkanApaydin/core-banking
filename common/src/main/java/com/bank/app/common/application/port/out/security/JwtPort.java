package com.bank.app.common.application.port.out.security;

public interface JwtPort {
    String extractUsername(String token);
    String generateToken(Long userId, String username);
    String generateToken(Long userId, String username, String role);
    String extractRole(String token);
    Long extractUserId(String token);
    boolean isTokenValid(String token);
    long getExpirationMs();
}
