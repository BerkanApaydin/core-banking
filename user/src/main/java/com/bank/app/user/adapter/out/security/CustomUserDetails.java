package com.bank.app.user.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class CustomUserDetails extends User implements AuthenticatedPrincipalPort {
    private static final long serialVersionUID = 1L;
    private final Long id;
    private final long tokenVersion;

    public CustomUserDetails(Long id, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, password, authorities, 0L);
    }

    public CustomUserDetails(Long id, String username, String password,
                             Collection<? extends GrantedAuthority> authorities, long tokenVersion) {
        super(username, password, authorities);
        this.id = id;
        this.tokenVersion = tokenVersion;
    }

    public Long getId() {
        return id;
    }

    public long getTokenVersion() {
        return tokenVersion;
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
