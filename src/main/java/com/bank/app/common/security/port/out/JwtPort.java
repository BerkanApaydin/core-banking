package com.bank.app.common.security.port.out;

import com.bank.app.user.domain.User;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtPort {
    String extractUsername(String token);
    String generateToken(User user);
    boolean isTokenValid(String token, UserDetails userDetails);
}
