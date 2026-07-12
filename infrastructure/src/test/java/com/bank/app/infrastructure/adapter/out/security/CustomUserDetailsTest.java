package com.bank.app.infrastructure.adapter.out.security;
import com.bank.app.user.adapter.out.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CustomUserDetailsTest {

    @Test
    void shouldCreateWithAllFields() {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(42L, "testuser", "password", authorities);

        assertEquals(42L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("password", user.getPassword());
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
        CustomUserDetails user = new CustomUserDetails(1L, "user", "pass", Collections.emptyList());

        assertTrue(user.getAuthorities().isEmpty());
    }
}
