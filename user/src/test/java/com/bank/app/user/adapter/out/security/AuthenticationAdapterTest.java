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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationAdapterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Test
    void shouldAuthenticateSuccessfully() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);

        adapter.authenticate("user", "pass");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("user", "pass"));
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
    void shouldExposeAuthenticationProviderFailureAsServiceUnavailable() {
        AuthenticationAdapter adapter = new AuthenticationAdapter(authenticationManager);
        doThrow(new AuthenticationServiceException("database unavailable"))
                .when(authenticationManager).authenticate(any());

        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> adapter.authenticate("user", "pass"));
    }
}
