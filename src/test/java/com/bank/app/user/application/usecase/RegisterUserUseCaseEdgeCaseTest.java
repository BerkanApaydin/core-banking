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
class RegisterUserUseCaseEdgeCaseTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoder passwordEncoder;

    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = new RegisterUserUseCase(loadUserPort, saveUserPort, passwordEncoder);
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
        AuthRequest request = new AuthRequest("newuser", "mypassword");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("mypassword")).thenReturn("encodedPassword");

        registerUserUseCase.execute(request);

        verify(loadUserPort).findByUsername("newuser");
        verify(passwordEncoder).encode("mypassword");
        verify(saveUserPort).save(argThat(user ->
                "newuser".equals(user.getUsername()) &&
                "encodedPassword".equals(user.getPassword()) &&
                "ROLE_USER".equals(user.getRole())
        ));
    }

    @Test
    void shouldPropagateSaveException() {
        AuthRequest request = new AuthRequest("newuser", "mypassword");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("mypassword")).thenReturn("encodedPassword");
        doThrow(new RuntimeException("DB error")).when(saveUserPort).save(any(User.class));

        assertThrows(RuntimeException.class,
                () -> registerUserUseCase.execute(request));
    }

    @Test
    void shouldEncodePasswordWithBCrypt() {
        AuthRequest request = new AuthRequest("newuser", "rawPassword123");
        when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawPassword123")).thenReturn("$2a$10$encryptedhash");

        registerUserUseCase.execute(request);

        verify(passwordEncoder).encode("rawPassword123");
        verify(saveUserPort).save(argThat(user ->
                "$2a$10$encryptedhash".equals(user.getPassword())
        ));
    }
}
