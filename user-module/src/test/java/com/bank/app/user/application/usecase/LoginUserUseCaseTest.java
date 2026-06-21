package com.bank.app.user.application.usecase;

import com.bank.app.common.security.JwtTokenProvider;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.bank.app.user.application.port.out.AuthenticationPort;
import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.core.env.Environment;
import java.util.Optional;
import com.bank.app.user.exception.UserNotFoundException;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock private AuthenticationPort authenticationPort;
    @Mock private LoadUserPort loadUserPort;
    private JwtTokenProvider jwtTokenProvider;
    private LoginUserUseCase loginUserUseCase;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(mock(Environment.class),
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                86400000L, true);
        loginUserUseCase = new LoginUserUseCase(authenticationPort, jwtTokenProvider, loadUserPort);
    }

    @Test
    void shouldLoginSuccessfully() {
        AuthRequest request = new AuthRequest("testuser", "password");
        User user = new User(100L, "testuser", "hashed", "ROLE_USER");

        doNothing().when(authenticationPort).authenticate(anyString(), anyString());
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.of(user));

        AuthResponse response = loginUserUseCase.execute(request);

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(100L, response.userId());
        assertEquals("testuser", response.username());

        verify(authenticationPort).authenticate(anyString(), anyString());
        verify(loadUserPort).findByUsername("testuser");
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
}
