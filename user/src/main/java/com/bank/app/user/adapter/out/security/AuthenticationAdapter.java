package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAdapter implements AuthenticationPort {

    private final AuthenticationManager authenticationManager;

    public AuthenticationAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void authenticate(String username, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (AuthenticationException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
    }
}
