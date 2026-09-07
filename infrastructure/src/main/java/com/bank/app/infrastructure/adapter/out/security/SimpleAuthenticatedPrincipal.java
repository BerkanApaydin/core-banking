package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Infrastructure-owned authenticated principal for token-derived logins
 * (user id + role come straight from verified JWT claims, no DB round-trip).
 * The DB-backed login path uses the user module's own principal type; both are
 * consumed through {@link AuthenticatedPrincipalPort} so no adapter depends on a
 * concrete adapter class from another module.
 */
public class SimpleAuthenticatedPrincipal extends User implements AuthenticatedPrincipalPort {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    public SimpleAuthenticatedPrincipal(Long userId, String username,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, "", authorities);
        this.userId = userId;
    }

    @Override
    public Long getAuthenticatedUserId() {
        return userId;
    }

    @Override
    public String getAuthenticatedUsername() {
        return getUsername();
    }
}
