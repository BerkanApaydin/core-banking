package com.bank.app.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityUtilsTest {

    private SecurityUtils securityUtils;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        securityUtils = new SecurityUtils();
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void testGetCurrentUserId_whenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);
        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void testGetCurrentUserId_whenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void testGetCurrentUserId_whenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void testGetCurrentUserId_whenAnonymousUserName() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void testGetCurrentUserId_whenPrincipalNotCustomUserDetails() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn("not_custom_user_details");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void testGetCurrentUserId_whenPrincipalIsCustomUserDetails() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(42L);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertTrue(userId.isPresent());
        assertEquals(42L, userId.get());
    }

    @Test
    void testGetCurrentUsername_whenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);
        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void testGetCurrentUsername_whenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void testGetCurrentUsername_whenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void testGetCurrentUsername_whenAnonymousUserName() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void testGetCurrentUsername_whenValidUser() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("john");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertTrue(username.isPresent());
        assertEquals("john", username.get());
    }

    @Test
    void testCheckUserAuthorization_whenNotLoggedIn() {
        SecurityContextHolder.getContext().setAuthentication(null);
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            securityUtils.checkUserAuthorization(42L, "Error message");
        });
        assertEquals("Oturum bulunamadı.", ex.getMessage());
    }

    @Test
    void testCheckUserAuthorization_whenUserIdMismatch() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(10L);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            securityUtils.checkUserAuthorization(42L, "Forbidden access");
        });
        assertEquals("Forbidden access", ex.getMessage());
    }

    @Test
    void testCheckUserAuthorization_whenAuthorized() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(42L);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertDoesNotThrow(() -> {
            securityUtils.checkUserAuthorization(42L, "Forbidden access");
        });
    }
}
