package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.domain.User;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUserUseCase")
class LoginUserUseCaseTest {

    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoadUserPort loadUserPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    private LoginUserUseCase loginUserUseCase;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password";
    private static final String CLIENT_IP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        loginUserUseCase = new LoginUserUseCaseImpl(authenticationPort, jwtPort, loadUserPort, loginAttemptPort);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should login successfully without client IP")
        void shouldLoginSuccessfully() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            User user = new User(new UserId(100L), USERNAME, "hashed", Role.ROLE_USER);

            doNothing().when(authenticationPort).authenticate(anyString(), anyString());
            when(loadUserPort.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER")).thenReturn("mock-jwt-token");

            AuthResponse response = loginUserUseCase.execute(request);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            assertThat(response.userId()).isEqualTo(100L);
            assertThat(response.username()).isEqualTo(USERNAME);

            verify(authenticationPort).authenticate(anyString(), anyString());
            verify(loadUserPort).findByUsername(USERNAME);
            verify(jwtPort).generateToken(100L, USERNAME, "ROLE_USER");
        }

        @Test
        @DisplayName("should login successfully with client IP and reset login attempts")
        void shouldLoginWithClientIpAndResetAttempts() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            User user = new User(new UserId(100L), USERNAME, "hashed", Role.ROLE_USER);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            doNothing().when(authenticationPort).authenticate(anyString(), anyString());
            when(loadUserPort.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER")).thenReturn("mock-jwt-token");

            AuthResponse response = loginUserUseCase.execute(request, CLIENT_IP);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(loginAttemptPort).reset(CLIENT_IP);
            verify(loginAttemptPort).resetByUsername(USERNAME);
        }

        @Test
        @DisplayName("should login successfully with null client IP")
        void shouldLoginWithNullClientIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            User user = new User(new UserId(100L), USERNAME, "hashed", Role.ROLE_USER);

            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            doNothing().when(authenticationPort).authenticate(anyString(), anyString());
            when(loadUserPort.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER")).thenReturn("mock-jwt-token");

            AuthResponse response = loginUserUseCase.execute(request, null);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(loginAttemptPort).isUsernameBlocked(USERNAME);
            verify(loginAttemptPort).resetByUsername(USERNAME);
            verify(loginAttemptPort, never()).reset(any());
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }
    }

    @Nested
    @DisplayName("IP blocking")
    class IpBlocking {

        @Test
        @DisplayName("should throw TooManyFailedLoginAttemptsException when IP is blocked")
        void shouldThrowWhenIpBlocked() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(true);
            when(loginAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(TooManyFailedLoginAttemptsException.class)
                    .hasMessageContaining("Too many failed login attempts from this IP")
                    .hasMessageContaining("15 minutes");

            verifyNoInteractions(authenticationPort);
        }

        @Test
        @DisplayName("should throw TooManyFailedLoginAttemptsException when username is blocked")
        void shouldThrowWhenUsernameBlocked() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(true);
            when(loginAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(TooManyFailedLoginAttemptsException.class)
                    .hasMessageContaining("Too many failed login attempts for this username");

            verifyNoInteractions(authenticationPort);
        }
    }

    @Nested
    @DisplayName("authentication failure")
    class AuthenticationFailure {

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            doNothing().when(authenticationPort).authenticate(anyString(), anyString());
            when(loadUserPort.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(authenticationPort).authenticate(anyString(), anyString());
            verify(loadUserPort).findByUsername(USERNAME);
        }

        @Test
        @DisplayName("should throw when credentials are invalid")
        void shouldThrowOnInvalidCredentials() {
            AuthRequest request = new AuthRequest(USERNAME, "wrong_password");

            doThrow(new AuthenticationFailedException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("Kimlik do\u011frulama ba\u015far\u0131s\u0131z: Bad credentials");

            verify(authenticationPort).authenticate(anyString(), anyString());
            verifyNoInteractions(loadUserPort);
        }

        @Test
        @DisplayName("should record login failure when authentication fails with client IP")
        void shouldRecordFailureOnAuthExceptionWithClientIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            doThrow(new AuthenticationFailedException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(loginAttemptPort).recordFailure(CLIENT_IP, USERNAME);
        }

        @Test
        @DisplayName("should propagate AuthenticationFailedException when username does not exist")
        void shouldThrowBadCredentialsWhenUsernameDoesNotExist() {
            AuthRequest request = new AuthRequest("nonexistent", PASSWORD);

            doThrow(new AuthenticationFailedException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
            verifyNoInteractions(loadUserPort);
        }

        @Test
        @DisplayName("should wrap unexpected exception and record failure with client IP")
        void shouldWrapUnexpectedExceptionWithIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            doThrow(new RuntimeException("Database connection lost"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Invalid username or password.");

            verify(loginAttemptPort).recordFailure(CLIENT_IP, USERNAME);
        }

        @Test
        @DisplayName("should wrap unexpected exception without client IP")
        void shouldWrapUnexpectedExceptionWithoutIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            doThrow(new RuntimeException("Service unavailable"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Invalid username or password.");

            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }
    }
}
