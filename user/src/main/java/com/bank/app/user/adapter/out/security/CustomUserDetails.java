package com.bank.app.user.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class CustomUserDetails extends User implements AuthenticatedPrincipalPort {
    private static final long serialVersionUID = 1L;
    private final Long id;
    private final long tokenVersion;
    private final boolean mfaEnabled;

    public CustomUserDetails(Long id, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, password, authorities, 0L);
    }

    public CustomUserDetails(Long id, String username, String password,
                             Collection<? extends GrantedAuthority> authorities, long tokenVersion) {
        this(id, username, password, authorities, tokenVersion, false);
    }

    public CustomUserDetails(Long id, String username, String password,
                             Collection<? extends GrantedAuthority> authorities, long tokenVersion,
                             boolean mfaEnabled) {
        super(username, password, authorities);
        this.id = id;
        this.tokenVersion = tokenVersion;
        this.mfaEnabled = mfaEnabled;
    }

    public Long getId() {
        return id;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
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
