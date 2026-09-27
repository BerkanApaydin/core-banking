package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAdapter implements AuthenticationPort {

    private final AuthenticationManager authenticationManager;

    public AuthenticationAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthenticatedUser authenticate(String username, String password) {
        try {
            var authenticated = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            if (authenticated == null || !authenticated.isAuthenticated()
                    || !(authenticated.getPrincipal() instanceof CustomUserDetails principal)
                    || authenticated.getAuthorities().size() != 1) {
                throw new AuthenticationServiceException("Authentication provider returned an invalid identity");
            }
            try {
                Role role = Role.valueOf(authenticated.getAuthorities().iterator().next().getAuthority());
                return new AuthenticatedUser(new UserId(principal.getId()), principal.getUsername(), role);
            } catch (IllegalArgumentException | NullPointerException invalidIdentity) {
                throw new AuthenticationServiceException("Authentication provider returned an invalid identity", invalidIdentity);
            }
        } catch (AuthenticationServiceException e) {
            throw new AuthenticationBackendUnavailableException(e);
        } catch (AuthenticationException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
    }
}
