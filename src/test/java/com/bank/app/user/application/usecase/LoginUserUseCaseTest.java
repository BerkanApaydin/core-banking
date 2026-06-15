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

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class LoginUserUseCaseTest {

    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private LoadUserPort loadUserPort;
    private LoginUserUseCase loginUserUseCase;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        loadUserPort = mock(LoadUserPort.class);
        loginUserUseCase = new LoginUserUseCase(authenticationManager, jwtService, loadUserPort);
    }

    @Test
    void shouldLoginSuccessfully() {
        AuthRequest request = new AuthRequest("testuser", "password");
        User user = new User(100L, "testuser", "hashed", "ROLE_USER");

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

        assertThrows(BadCredentialsException.class, () -> 
                loginUserUseCase.execute(request)
        );

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(loadUserPort);
    }
}
