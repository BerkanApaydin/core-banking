package com.bank.app.common.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.bank.app.common.security.CustomUserDetails;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityContextAdapterTest {

    private SecurityContextAdapter securityUtils;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        securityUtils = new SecurityContextAdapter();
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void shouldReturnEmptyWhenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);
        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenAnonymousUserName() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenPrincipalNotCustomUserDetails() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn("not_custom_user_details");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnUserIdWhenPrincipalIsCustomUserDetails() {
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
    void shouldReturnEmptyUsernameWhenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);
        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void shouldReturnEmptyUsernameWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void shouldReturnEmptyUsernameWhenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void shouldReturnEmptyUsernameWhenAnonymousUserName() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertFalse(username.isPresent());
    }

    @Test
    void shouldReturnUsernameWhenValidUser() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("john");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertTrue(username.isPresent());
        assertEquals("john", username.get());
    }

    @Test
    void shouldThrowWhenNotLoggedIn() {
        SecurityContextHolder.getContext().setAuthentication(null);
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            securityUtils.checkUserAuthorization(42L, "Error message");
        });
        assertEquals("Oturum bulunamadı.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenUserIdMismatch() {
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
    void shouldNotThrowWhenAuthorized() {
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
