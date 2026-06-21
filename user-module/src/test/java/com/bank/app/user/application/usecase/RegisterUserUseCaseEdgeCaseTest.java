package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseEdgeCaseTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoderPort passwordEncoderPort;

    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort);
    }

    @Test
    void shouldThrowWhenUsernameAlreadyExists() {
        AuthRequest request = new AuthRequest("existing", "password");
        when(loadUserPort.findByUsername("existing"))
                .thenReturn(Optional.of(new User(1L, "existing", "hash", "ROLE_USER")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registerUserUseCase.execute(request));
        assertEquals("Kullanıcı adı zaten kullanımda.", ex.getMessage());
        verify(saveUserPort, never()).save(any());
    }

    @Test
    void shouldRegisterUserSuccessfullyWhenUsernameDoesNotExist() {
        AuthRequest request = new AuthRequest("newuser", "Mypasswor1");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("Mypasswor1")).thenReturn("encodedPassword");

        registerUserUseCase.execute(request);

        verify(loadUserPort).findByUsername("newuser");
        verify(passwordEncoderPort).encode("Mypasswor1");
        verify(saveUserPort).save(argThat(user ->
                "newuser".equals(user.getUsername()) &&
                "encodedPassword".equals(user.getPassword()) &&
                "ROLE_USER".equals(user.getRole())
        ));
    }

    @Test
    void shouldPropagateSaveException() {
        AuthRequest request = new AuthRequest("newuser", "Mypasswor1");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("Mypasswor1")).thenReturn("encodedPassword");
        doThrow(new RuntimeException("DB error")).when(saveUserPort).save(any(User.class));

        assertThrows(RuntimeException.class,
                () -> registerUserUseCase.execute(request));
    }

    @Test
    void shouldThrowWhenPasswordViolatesPolicy() {
        AuthRequest request = new AuthRequest("newuser", "weak");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registerUserUseCase.execute(request));
        assertTrue(ex.getMessage().contains("en az"));
        verify(loadUserPort).findByUsername("newuser");
        verifyNoInteractions(passwordEncoderPort);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    void shouldEncodePasswordWithBCrypt() {
        AuthRequest request = new AuthRequest("newuser", "rawPassword123");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("rawPassword123")).thenReturn("$2a$10$encryptedhash");

        registerUserUseCase.execute(request);

        verify(passwordEncoderPort).encode("rawPassword123");
        verify(saveUserPort).save(argThat(user ->
                "$2a$10$encryptedhash".equals(user.getPassword())
        ));
    }
}
