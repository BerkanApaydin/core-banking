package com.bank.app.user.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringAuthenticationAdapterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    private SpringAuthenticationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringAuthenticationAdapter(authenticationManager);
    }

    @Test
    void shouldAuthenticateSuccessfully() {
        adapter.authenticate("testuser", "password");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("testuser", "password"));
    }

    @Test
    void shouldPropagateBadCredentialsException() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class,
                () -> adapter.authenticate("testuser", "wrong_password"));
    }

    @Test
    void shouldPropagateAuthenticationException() {
        doThrow(new RuntimeException("Authentication failed"))
                .when(authenticationManager).authenticate(any());

        assertThrows(RuntimeException.class,
                () -> adapter.authenticate("testuser", "password"));
    }
}
