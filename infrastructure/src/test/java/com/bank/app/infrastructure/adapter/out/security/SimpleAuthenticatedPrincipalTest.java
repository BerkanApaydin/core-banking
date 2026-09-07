package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleAuthenticatedPrincipalTest {

    @Test
    void shouldExposeUserIdAndUsernameThroughPortAbstraction() {
        SimpleAuthenticatedPrincipal principal = new SimpleAuthenticatedPrincipal(42L, "jwtuser",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        AuthenticatedPrincipalPort portView = principal;

        assertEquals(42L, portView.getAuthenticatedUserId());
        assertEquals("jwtuser", portView.getAuthenticatedUsername());
        assertEquals("jwtuser", principal.getUsername());
        assertTrue(principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
