package com.bank.app.user.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class CustomUserDetails extends User implements AuthenticatedPrincipalPort {
    private static final long serialVersionUID = 1L;
    private final Long id;

    public CustomUserDetails(Long id, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getAuthenticatedUserId() {
        return id;
    }

    @Override
    public String getAuthenticatedUsername() {
        return getUsername();
    }
}
