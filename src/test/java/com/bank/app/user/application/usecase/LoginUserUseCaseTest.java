package com.bank.app.user.application.usecase;

import com.bank.app.common.security.JwtService;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.core.env.Environment;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private LoadUserPort loadUserPort;
    private JwtService jwtService;
    private LoginUserUseCase loginUserUseCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(mock(Environment.class),
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                86400000L, true);
        loginUserUseCase = new LoginUserUseCase(authenticationManager, jwtService, loadUserPort);
    }

    @Test
    void shouldLoginSuccessfully() {
        AuthRequest request = new AuthRequest("testuser", "password");
        User user = new User(100L, "testuser", "hashed", "ROLE_USER");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.of(user));

        AuthResponse response = loginUserUseCase.execute(request);

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(100L, response.userId());
        assertEquals("testuser", response.username());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(loadUserPort).findByUsername("testuser");
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        AuthRequest request = new AuthRequest("testuser", "password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                loginUserUseCase.execute(request)
        );

        assertEquals("Kullanıcı bulunamadı", exception.getMessage());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(loadUserPort).findByUsername("testuser");
    }

    @Test
    void shouldPropagateAuthenticationExceptionWhenCredentialsAreInvalid() {
        AuthRequest request = new AuthRequest("testuser", "wrong_password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> 
                loginUserUseCase.execute(request)
        );
        assertEquals("Bad credentials", exception.getMessage());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(loadUserPort);
    }
}
