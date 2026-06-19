package com.bank.app.common.security.port.out;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtPort {
    String extractUsername(String token);
    String generateToken(Long userId, String username);
    boolean isTokenValid(String token, UserDetails userDetails);
}
