package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AuthenticationAdapter implements AuthenticationPort {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationAdapter.class);

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
                // Fail-closed parse: unknown/blank authority strings throw with
                // a standard message instead of bypassing Role validation.
                Role role = Role.require(authenticated.getAuthorities().iterator().next().getAuthority());
                if (principal.getId() == null) {
                    // Explicit check: new UserId(null) would throw NPE, and
                    // catching NPE here would mask unrelated programming bugs.
                    throw new IllegalArgumentException("Principal id must not be null");
                }
                return new AuthenticatedUser(new UserId(principal.getId()), principal.getUsername(), role,
                        principal.getTokenVersion(), principal.isMfaEnabled());
            } catch (IllegalArgumentException invalidIdentity) {
                throw new AuthenticationServiceException("Authentication provider returned an invalid identity", invalidIdentity);
            }
        } catch (UsernameNotFoundException e) {
            // R-U15: SecurityConfig disables Spring's default hiding so the
            // unknown-user path reaches the application layer, which burns a
            // dummy BCrypt and returns the same generic 401. The framework
            // message may embed the username, so never forward it.
            throw new UserNotFoundException("User not found");
        } catch (AuthenticationServiceException e) {
            throw new AuthenticationBackendUnavailableException(e);
        } catch (AuthenticationException e) {
            // D11/K13: never forward the framework message — it may carry the
            // username ("User not found: x") and become a user-enumeration
            // oracle if any future message template interpolates args.
            // The cause stays attached for server-side diagnostics; the
            // client-facing message is always the fixed authentication text.
            log.debug("Credential check failed: {}", e.getClass().getSimpleName());
            throw new AuthenticationFailedException(e);
        }
    }
}
