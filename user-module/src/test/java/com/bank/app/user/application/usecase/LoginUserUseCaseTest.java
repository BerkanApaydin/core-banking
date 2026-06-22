package com.bank.app.user.application.usecase;

import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.exception.UserNotFoundException;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoadUserPort loadUserPort;
    private LoginUserUseCase loginUserUseCase;

    @BeforeEach
    void setUp() {
        loginUserUseCase = new LoginUserUseCaseImpl(authenticationPort, jwtPort, loadUserPort);
    }

    @Test
    void shouldLoginSuccessfully() {
        AuthRequest request = new AuthRequest("testuser", "password");
        User user = new User(100L, "testuser", "hashed", "ROLE_USER");

        doNothing().when(authenticationPort).authenticate(anyString(), anyString());
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtPort.generateToken(100L, "testuser", "ROLE_USER")).thenReturn("mock-jwt-token");

        AuthResponse response = loginUserUseCase.execute(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.token());
        assertEquals(100L, response.userId());
        assertEquals("testuser", response.username());

        verify(authenticationPort).authenticate(anyString(), anyString());
        verify(loadUserPort).findByUsername("testuser");
        verify(jwtPort).generateToken(100L, "testuser", "ROLE_USER");
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        AuthRequest request = new AuthRequest("testuser", "password");

        doNothing().when(authenticationPort).authenticate(anyString(), anyString());
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> 
                loginUserUseCase.execute(request)
        );

        assertEquals("Kullanıcı bulunamadı", exception.getMessage());
        verify(authenticationPort).authenticate(anyString(), anyString());
        verify(loadUserPort).findByUsername("testuser");
    }

    @Test
    void shouldPropagateAuthenticationExceptionWhenCredentialsAreInvalid() {
        AuthRequest request = new AuthRequest("testuser", "wrong_password");

        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> 
                loginUserUseCase.execute(request)
        );
        assertEquals("Bad credentials", exception.getMessage());

        verify(authenticationPort).authenticate(anyString(), anyString());
        verifyNoInteractions(loadUserPort);
    }

    @Test
    void shouldThrowBadCredentialsWhenUsernameDoesNotExist() {
        AuthRequest request = new AuthRequest("nonexistent", "password");

        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

        assertThrows(BadCredentialsException.class,
                () -> loginUserUseCase.execute(request));
        verifyNoInteractions(loadUserPort);
    }
}
