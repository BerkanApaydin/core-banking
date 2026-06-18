package com.bank.app.user.application.usecase;

import com.bank.app.common.security.JwtService;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseEdgeCaseTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private LoadUserPort loadUserPort;

    private JwtService jwtService;
    private LoginUserUseCase loginUserUseCase;

    @BeforeEach
    void setUp() {
        Environment environment = mock(Environment.class);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
        jwtService = new JwtService(environment);
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", true);
        loginUserUseCase = new LoginUserUseCase(authenticationManager, jwtService, loadUserPort);
    }

    @Test
    void shouldLoginSuccessfullyAndReturnToken() {
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
    }

    @Test
    void shouldThrowWhenUserNotFoundAfterAuthentication() {
        AuthRequest request = new AuthRequest("testuser", "password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(loadUserPort.findByUsername("testuser")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> loginUserUseCase.execute(request));
        assertEquals("Kullanıcı bulunamadı", ex.getMessage());
    }

    @Test
    void shouldThrowBadCredentialsWhenPasswordWrong() {
        AuthRequest request = new AuthRequest("testuser", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> loginUserUseCase.execute(request));
        verifyNoInteractions(loadUserPort);
    }

    @Test
    void shouldThrowBadCredentialsWhenUsernameDoesNotExist() {
        AuthRequest request = new AuthRequest("nonexistent", "password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> loginUserUseCase.execute(request));
        verifyNoInteractions(loadUserPort);
    }
}
