package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.User;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterUserUseCase")
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
        registerUserUseCase = new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort, PasswordPolicy.DEFAULT);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should register user successfully")
        void shouldRegisterSuccessfully() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("hashedpassword");

            registerUserUseCase.execute(request);

            verify(loadUserPort).findByUsername("newuser");
            verify(passwordEncoderPort).encode("Rawpassword1");
            verify(saveUserPort).save(argThat(user ->
                    "newuser".equals(user.getUsername()) &&
                    "hashedpassword".equals(user.getPassword()) &&
                    user.getRole() == Role.ROLE_USER));
        }

        @Test
        @DisplayName("should register user with email and phone")
        void shouldRegisterWithEmailAndPhone() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1", "test@example.com", "5551234567");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("hashedpassword");

            registerUserUseCase.execute(request);

            verify(saveUserPort).save(argThat(user ->
                    "newuser".equals(user.getUsername()) &&
                    "hashedpassword".equals(user.getPassword()) &&
                    user.getEmail() != null && "test@example.com".equals(user.getEmail().value()) &&
                    user.getPhone() != null && "5551234567".equals(user.getPhone().value())
            ));
        }

        @Test
        @DisplayName("should register user with null email and phone")
        void shouldRegisterWithNullEmailAndPhone() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("hashedpassword");

            registerUserUseCase.execute(request);

            verify(saveUserPort).save(argThat(user ->
                    user.getEmail() == null && user.getPhone() == null
            ));
        }

        @Test
        @DisplayName("should encode password")
        void shouldEncodePassword() {
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

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw when username already exists")
        void shouldThrowOnDuplicateUsername() {
            AuthRequest request = new AuthRequest("existinguser", "password");
            User existingUser = new User(new UserId(1L), "existinguser", "hashed", Role.ROLE_USER);

            when(loadUserPort.findByUsername("existinguser")).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username already in use.");

            verify(loadUserPort).findByUsername("existinguser");
            verifyNoInteractions(passwordEncoderPort);
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should throw when password violates policy")
        void shouldThrowOnWeakPassword() {
            AuthRequest request = new AuthRequest("newuser", "weak");

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least");

            verify(loadUserPort).findByUsername("newuser");
            verifyNoInteractions(passwordEncoderPort);
            verify(saveUserPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("should propagate save exception")
        void shouldPropagateSaveException() {
            AuthRequest request = new AuthRequest("newuser", "Mypasswor1");
            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Mypasswor1")).thenReturn("encodedPassword");
            doThrow(new RuntimeException("DB error")).when(saveUserPort).save(any(User.class));

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(RuntimeException.class);
        }
    }
}
