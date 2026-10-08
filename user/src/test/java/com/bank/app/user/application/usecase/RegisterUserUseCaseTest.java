package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.UserRegisteredEvent;
import com.bank.app.user.domain.exception.UsernameAlreadyTakenException;
import com.bank.app.user.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.time.Clock;

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
    @Mock
    private DomainEventPublisherService domainEventPublisherService;
    @Mock
    private ClockProviderPort clockProvider;

    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        lenient().when(clockProvider.clock()).thenReturn(Clock.systemUTC());
        lenient().when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new User(new UserId(42L), user.getUsername(), user.getPassword(), user.getRole(),
                    user.getEmail(), user.getPhone());
        });
        registerUserUseCase = new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort,
                PasswordPolicy.DEFAULT, domainEventPublisherService, clockProvider);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should register user successfully")
        void shouldRegisterSuccessfully() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("$2a$10$hashedpasswordhashhashedpass01");

            registerUserUseCase.execute(request);

            verify(loadUserPort).findByUsername("newuser");
            verify(passwordEncoderPort).encode("Rawpassword1");
            verify(saveUserPort).save(argThat(user -> "newuser".equals(user.getUsername()) &&
                    "$2a$10$hashedpasswordhashhashedpass01".equals(user.getPassword()) &&
                    user.getRole() == Role.ROLE_USER));
            verify(domainEventPublisherService).publishEvents(any(User.class));
            verify(domainEventPublisherService).publishEvents(argThat(user ->
                    user.getDomainEvents().stream().anyMatch(event -> event instanceof
                            UserRegisteredEvent registration
                            && "42".equals(registration.userId()))));
        }

        @Test
        @DisplayName("should register user with email and phone")
        void shouldRegisterWithEmailAndPhone() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1", "test@example.com", "5551234567");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("$2a$10$hashedpasswordhashhashedpass01");

            registerUserUseCase.execute(request);

            verify(saveUserPort).save(argThat(user -> "newuser".equals(user.getUsername()) &&
                    "$2a$10$hashedpasswordhashhashedpass01".equals(user.getPassword()) &&
                    user.getEmail() != null && "test@example.com".equals(user.getEmail().value()) &&
                    user.getPhone() != null && "5551234567".equals(user.getPhone().value())));
        }

        @Test
        @DisplayName("should register user with null email and phone")
        void shouldRegisterWithNullEmailAndPhone() {
            AuthRequest request = new AuthRequest("newuser", "Rawpassword1");

            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Rawpassword1")).thenReturn("$2a$10$hashedpasswordhashhashedpass01");

            registerUserUseCase.execute(request);

            verify(saveUserPort).save(argThat(user -> user.getEmail() == null && user.getPhone() == null));
        }

        @Test
        @DisplayName("should encode password")
        void shouldEncodePassword() {
            AuthRequest request = new AuthRequest("newuser", "rawPassword123");
            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("rawPassword123")).thenReturn("$2a$10$encryptedhash");

            registerUserUseCase.execute(request);

            verify(passwordEncoderPort).encode("rawPassword123");
            verify(saveUserPort).save(argThat(user -> "$2a$10$encryptedhash".equals(user.getPassword())));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw when username already exists")
        void shouldThrowOnDuplicateUsername() {
            AuthRequest request = new AuthRequest("existinguser", "$2a$12$testpasswordhash00000000000000000000001");
            User existingUser = new User(new UserId(1L), "existinguser", "$2a$12$testhashedhash0000000000000000000000001", Role.ROLE_USER);

            when(loadUserPort.findByUsername("existinguser")).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(UsernameAlreadyTakenException.class)
                    .hasMessage("Username is already in use: existinguser");

            verify(loadUserPort).findByUsername("existinguser");
            verifyNoInteractions(passwordEncoderPort);
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should throw when password violates policy")
        void shouldThrowOnWeakPassword() {
            AuthRequest request = new AuthRequest("newuser", "weak");

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(WeakPasswordException.class)
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
            AuthRequest request = new AuthRequest("newuser", "Mypasswor123");
            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Mypasswor123")).thenReturn("$2a$10$encodedPasswordHashEncodedPw02");
            doThrow(new RuntimeException("DB error")).when(saveUserPort).save(any(User.class));

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should map concurrent duplicate to UsernameAlreadyTakenException")
        void shouldMapConcurrentDuplicateToConflict() {
            AuthRequest request = new AuthRequest("newuser", "Mypasswor123");
            when(loadUserPort.findByUsername("newuser")).thenReturn(Optional.empty());
            when(passwordEncoderPort.encode("Mypasswor123")).thenReturn("$2a$10$encodedPasswordHashEncodedPw02");
            doThrow(new DataIntegrityViolationException("duplicate key"))
                    .when(saveUserPort).save(any(User.class));

            assertThatThrownBy(() -> registerUserUseCase.execute(request))
                    .isExactlyInstanceOf(UsernameAlreadyTakenException.class);
        }
    }
}
