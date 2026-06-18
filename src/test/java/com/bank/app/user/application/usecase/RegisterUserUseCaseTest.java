package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.application.port.SaveUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoder passwordEncoder;
    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = new RegisterUserUseCase(loadUserPort, saveUserPort, passwordEncoder);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        AuthRequest request = new AuthRequest("newuser", "rawpassword");

        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawpassword")).thenReturn("hashedpassword");

        registerUserUseCase.execute(request);

        verify(loadUserPort).findByUsername("newuser");
        verify(passwordEncoder).encode("rawpassword");
        verify(saveUserPort).save(argThat(user -> 
                "newuser".equals(user.getUsername()) &&
                "hashedpassword".equals(user.getPassword()) &&
                "ROLE_USER".equals(user.getRole())
        ));
    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() {
        AuthRequest request = new AuthRequest("existinguser", "password");
        User existingUser = new User(1L, "existinguser", "hashed", "ROLE_USER");

        when(loadUserPort.findByUsername("existinguser")).thenReturn(Optional.of(existingUser));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                registerUserUseCase.execute(request)
        );

        assertEquals("Kullanıcı adı zaten kullanımda.", exception.getMessage());
        verify(loadUserPort).findByUsername("existinguser");
        verifyNoInteractions(passwordEncoder);
        verify(saveUserPort, never()).save(any());
    }
}
