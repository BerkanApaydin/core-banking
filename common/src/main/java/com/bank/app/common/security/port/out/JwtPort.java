package com.bank.app.common.security.port.out;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtPort {
    String extractUsername(String token);
    default String generateToken(Long userId, String username) {
        return generateToken(userId, username, "ROLE_USER");
    }
    String generateToken(Long userId, String username, String role);
    String extractRole(String token);
    Long extractUserId(String token);
    boolean isTokenValid(String token, UserDetails userDetails);
    boolean isTokenValid(String token);
}
