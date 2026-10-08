package com.bank.app.user.adapter.out.security;

import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CustomUserDetailsTest {

    @Test
    void shouldCreateWithAllFields() {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(42L, "testuser", "$2a$12$testpasswordhash00000000000000000000001", authorities);

        assertEquals(42L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("$2a$12$testpasswordhash00000000000000000000001", user.getPassword());
        assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void shouldCreateWithEmptyPassword() {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(1L, "user", "", authorities);

        assertEquals("", user.getPassword());
        assertEquals(1L, user.getId());
    }

    @Test
    void shouldHandleEmptyAuthorities() {
        CustomUserDetails user = new CustomUserDetails(1L, "user", "$2a$12$testpasshash00000000000000000000000001", Collections.emptyList());

        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    void shouldExposeFrameworkFreePrincipalView() {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(42L, "testuser", "$2a$12$testpasswordhash00000000000000000000001", authorities);

        AuthenticatedPrincipalPort principal = user;

        assertEquals(42L, principal.getAuthenticatedUserId());
        assertEquals("testuser", principal.getAuthenticatedUsername());
    }

    @Test
    void shouldDefaultTokenVersionToZeroAndAcceptExplicitVersion() {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        assertEquals(0L, new CustomUserDetails(42L, "testuser", "$2a$12$testpasswordhash00000000000000000000001", authorities).getTokenVersion());
        assertEquals(9L, new CustomUserDetails(42L, "testuser", "$2a$12$testpasswordhash00000000000000000000001", authorities, 9L).getTokenVersion());
    }
}
