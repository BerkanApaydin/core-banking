package com.bank.app.infrastructure.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.bank.app.infrastructure.adapter.out.security.SecurityContextAdapter;
import com.bank.app.infrastructure.adapter.out.security.SimpleAuthenticatedPrincipal;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
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
    void shouldReturnEmptyWhenPrincipalNotAuthenticatedPrincipal() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn("not_an_authenticated_principal");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertFalse(userId.isPresent());
    }

    @Test
    void shouldReturnUserIdWhenPrincipalIsAuthenticatedPrincipal() {
        AuthenticatedPrincipalPort principal = mock(AuthenticatedPrincipalPort.class);
        when(principal.getAuthenticatedUserId()).thenReturn(42L);

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
    void shouldReturnUserIdWhenPrincipalIsInfrastructurePrincipal() {
        SimpleAuthenticatedPrincipal principal = new SimpleAuthenticatedPrincipal(7L, "jwtuser",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("jwtuser");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = securityUtils.getCurrentUserId();
        assertTrue(userId.isPresent());
        assertEquals(7L, userId.get());
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
    void shouldReturnUsernameFromAuthenticatedPrincipal() {
        AuthenticatedPrincipalPort principal = mock(AuthenticatedPrincipalPort.class);
        when(principal.getAuthenticatedUsername()).thenReturn("principal-user");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("principal-user");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> username = securityUtils.getCurrentUsername();
        assertTrue(username.isPresent());
        assertEquals("principal-user", username.get());
    }

    @Test
    void shouldThrowWhenNotLoggedIn() {
        SecurityContextHolder.getContext().setAuthentication(null);
        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> {
            securityUtils.checkUserAuthorization(42L, "Error message");
        });
        assertEquals("Session not found.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenUserIdMismatch() {
        AuthenticatedPrincipalPort principal = mock(AuthenticatedPrincipalPort.class);
        when(principal.getAuthenticatedUserId()).thenReturn(10L);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> {
            securityUtils.checkUserAuthorization(42L, "Forbidden access");
        });
        assertEquals("Forbidden access", ex.getMessage());
    }

    @Test
    void shouldNotThrowWhenAuthorized() {
        AuthenticatedPrincipalPort principal = mock(AuthenticatedPrincipalPort.class);
        when(principal.getAuthenticatedUserId()).thenReturn(42L);

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
