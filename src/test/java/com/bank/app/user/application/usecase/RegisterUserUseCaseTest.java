package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private PasswordEncoderPort passwordEncoderPort;
    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = new RegisterUserUseCase(loadUserPort, saveUserPort, passwordEncoderPort);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        AuthRequest request = new AuthRequest("newuser", "Rawpassword1");

        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("hashedpassword");

        registerUserUseCase.execute(request);

        verify(loadUserPort).findByUsername("newuser");
        verify(passwordEncoderPort).encode("Rawpassword1");
        verify(saveUserPort).save(argThat(user -> "newuser".equals(user.getUsername()) &&
                "hashedpassword".equals(user.getPassword()) &&
                "ROLE_USER".equals(user.getRole())));
    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() {
        AuthRequest request = new AuthRequest("existinguser", "password");
        User existingUser = new User(1L, "existinguser", "hashed", "ROLE_USER");

        when(loadUserPort.findByUsername("existinguser")).thenReturn(Optional.of(existingUser));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registerUserUseCase.execute(request));

        assertEquals("Kullanıcı adı zaten kullanımda.", exception.getMessage());
        verify(loadUserPort).findByUsername("existinguser");
        verifyNoInteractions(passwordEncoderPort);
        verify(saveUserPort, never()).save(any());
    }
}
