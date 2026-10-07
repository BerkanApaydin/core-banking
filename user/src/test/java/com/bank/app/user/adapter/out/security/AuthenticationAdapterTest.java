package com.bank.app.user.adapter.out.security;

import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.bank.app.user.domain.Role;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationAdapterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Test
    void shouldAuthenticateSuccessfully() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        var principal = new CustomUserDetails(42L, "user", "encoded",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        var authenticated = adapter.authenticate("user", "pass");
        assertEquals(42L, authenticated.id().value());
        assertEquals("user", authenticated.username());
        assertEquals(Role.ROLE_ADMIN, authenticated.role());

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("user", "pass"));
    }

    @Test
    void shouldPropagateTokenVersionFromPrincipal() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        var principal = new CustomUserDetails(42L, "user", "encoded",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), 11L);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        var authenticated = adapter.authenticate("user", "pass");

        assertEquals(11L, authenticated.tokenVersion());
    }

    @Test
    void shouldRejectMissingProviderResult() {
        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> new AuthenticationAdapter(authenticationManager).authenticate("user", "pass"));
    }

    @Test
    void shouldRejectUnauthenticatedProviderResult() {
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("user", "pass"));
        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> new AuthenticationAdapter(authenticationManager).authenticate("user", "pass"));
    }

    @Test
    void shouldRejectUnexpectedPrincipalType() {
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> new AuthenticationAdapter(authenticationManager).authenticate("user", "pass"));
    }

    @Test
    void shouldRejectMissingOrUnknownAuthority() {
        var principal = new CustomUserDetails(42L, "user", "encoded", List.of());
        for (var authorities : List.of(List.<SimpleGrantedAuthority>of(),
                List.of(new SimpleGrantedAuthority("UNSUPPORTED")),
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")))) {
            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            assertThrows(AuthenticationBackendUnavailableException.class,
                    () -> new AuthenticationAdapter(authenticationManager).authenticate("user", "pass"));
        }
    }

    @Test
    void shouldThrowAuthenticationFailedExceptionOnFailure() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThrows(AuthenticationFailedException.class,
                () -> adapter.authenticate("user", "wrong"));
    }

    @Test
    void shouldNeverForwardFrameworkMessageOutward() {
        // D11/K13: the provider message (e.g. "User not found: admin") must not
        // reach the client-facing exception — latent user-enumeration guard.
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        doThrow(new BadCredentialsException("User not found: admin"))
                .when(authenticationManager).authenticate(any());

        var thrown = assertThrows(AuthenticationFailedException.class,
                () -> adapter.authenticate("user", "wrong"));
        org.junit.jupiter.api.Assertions.assertFalse(thrown.getMessage().contains("admin"));
    }

    @Test
    void shouldExposeAuthenticationProviderFailureAsServiceUnavailable() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        doThrow(new AuthenticationServiceException("database unavailable"))
                .when(authenticationManager).authenticate(any());

        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> adapter.authenticate("user", "pass"));
    }
}
