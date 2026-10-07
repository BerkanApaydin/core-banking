package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.AuthenticationPort.AuthenticatedUser;
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
import java.time.Clock;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUserUseCase")
class LoginUserUseCaseTest {

    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private ClockProviderPort clockProvider;
    @Mock private com.bank.app.common.application.port.out.AuditEventPort auditEventPort;
    private LoginUserUseCase loginUserUseCase;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password";
    private static final String CLIENT_IP = "192.168.1.1";


    @BeforeEach
    void setUp() {
        loginUserUseCase = new LoginUserUseCaseImpl(authenticationPort, jwtPort, loginAttemptPort,
                refreshTokenPort, clockProvider, auditEventPort);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should login successfully without client IP")
        void shouldLoginSuccessfully() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            AuthResponse response = loginUserUseCase.execute(request);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
            assertThat(response.userId()).isEqualTo(100L);
            assertThat(response.username()).isEqualTo(USERNAME);
            assertThat(response.expiresInMs()).isEqualTo(900000L);

            verify(authenticationPort).authenticate(anyString(), anyString());
            verify(jwtPort).generateToken(100L, USERNAME, "ROLE_USER", 0L);
            verify(jwtPort).generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L);
            verify(refreshTokenPort).save(anyString(), eq(100L), anyString(), any());
        }

        @Test
        @DisplayName("should stamp tokens with the user's current token version")
        void shouldStampTokensWithCurrentVersion() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER, 5L);

            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 5L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 5L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            AuthResponse response = loginUserUseCase.execute(request);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(jwtPort).generateToken(100L, USERNAME, "ROLE_USER", 5L);
            verify(jwtPort).generateRefreshToken(100L, USERNAME, "ROLE_USER", 5L);
        }

        @Test
        @DisplayName("should login successfully with client IP and reset login attempts")
        void shouldLoginWithClientIpAndResetAttempts() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            AuthResponse response = loginUserUseCase.execute(request, CLIENT_IP);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
            verify(loginAttemptPort).reset(CLIENT_IP);
            verify(loginAttemptPort).resetByUsername(USERNAME);
            verify(refreshTokenPort).save(anyString(), eq(100L), anyString(), any());
        }

        @Test
        @DisplayName("should login successfully with null client IP")
        void shouldLoginWithNullClientIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            AuthResponse response = loginUserUseCase.execute(request, null);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(loginAttemptPort).isUsernameBlocked(USERNAME);
            verify(loginAttemptPort).resetByUsername(USERNAME);
            verify(loginAttemptPort, never()).reset(any());
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("should fail closed when the refresh session cannot be persisted")
        void shouldFailClosedWhenSessionPersistenceFails() {
            // A session the server cannot rotate or revoke must never be
            // handed out: the storage failure fails the login even though
            // credentials were valid and tokens were already minted.
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());
            doThrow(new RuntimeException("session store down"))
                    .when(refreshTokenPort).save(anyString(), eq(100L), anyString(), any());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("session store down");
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
        void shouldNotAuthenticateWhenAttemptStoreIsUnavailableDuringGuardCheck() {
            when(loginAttemptPort.isIpBlocked(CLIENT_IP))
                    .thenThrow(new LoginAttemptStoreUnavailableException(new RuntimeException("Redis down")));

            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD), CLIENT_IP))
                    .isInstanceOf(LoginAttemptStoreUnavailableException.class);
            verifyNoInteractions(authenticationPort, jwtPort);
        }

        @Test
        void shouldNotReturnTokenWhenAttemptStoreFailsDuringReset() {
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            doThrow(new LoginAttemptStoreUnavailableException(new RuntimeException("Redis down")))
                    .when(loginAttemptPort).reset(CLIENT_IP);

            // Guard reset happens before any token is minted: nothing to revoke.
            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD), CLIENT_IP))
                    .isInstanceOf(LoginAttemptStoreUnavailableException.class);
            verify(loginAttemptPort).reset(CLIENT_IP);
            verify(loginAttemptPort, never()).resetByUsername(USERNAME);
            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verify(jwtPort, never()).generateToken(any(), any(), any(), anyLong());
            verify(jwtPort, never()).generateRefreshToken(any(), any(), any(), anyLong());
            verify(refreshTokenPort, never()).save(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            doThrow(new UserNotFoundException("User not found")).when(authenticationPort).authenticate(USERNAME, PASSWORD);

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(authenticationPort).authenticate(anyString(), anyString());
        }

        @Test
        @DisplayName("should throw when credentials are invalid")
        void shouldThrowOnInvalidCredentials() {
            AuthRequest request = new AuthRequest(USERNAME, "wrong_password");

            doThrow(new AuthenticationFailedException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("Authentication failed: Bad credentials");

            verify(authenticationPort).authenticate(anyString(), anyString());
            verifyNoInteractions(jwtPort);
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
            verifyNoInteractions(jwtPort);
        }

        @Test
        @DisplayName("should not count a backend failure as invalid credentials")
        void shouldNotCountBackendFailureAsInvalidCredentials() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(false);
            AuthenticationBackendUnavailableException failure = new AuthenticationBackendUnavailableException(
                    new RuntimeException("Database connection lost"));
            doThrow(failure).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isSameAs(failure);

            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verifyNoInteractions(jwtPort);
        }

        @Test
        @DisplayName("should propagate unexpected errors rather than turn them into bad credentials")
        void shouldPropagateUnexpectedExceptionWithoutIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            RuntimeException failure = new IllegalStateException("Service unavailable");
            doThrow(failure).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isSameAs(failure);

            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }

        @Test
        void shouldPropagateCredentialLookupFailureWithoutIssuingTokenOrCountingAttempt() {
            AuthenticationBackendUnavailableException failure = new AuthenticationBackendUnavailableException(
                    new RuntimeException("Database unavailable"));
            doThrow(failure).when(authenticationPort).authenticate(USERNAME, PASSWORD);

            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD), CLIENT_IP))
                    .isSameAs(failure);

            verifyNoInteractions(jwtPort);
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }
    }

    @Nested
    @DisplayName("authentication audit (K11/D4)")
    class AuthenticationAudit {

        private org.mockito.ArgumentCaptor<com.bank.app.common.domain.event.AuditEvent> auditCaptor() {
            return org.mockito.ArgumentCaptor
                    .forClass(com.bank.app.common.domain.event.AuditEvent.class);
        }

        @Test
        @DisplayName("should publish LOGIN_SUCCEEDED with the username")
        void shouldPublishLoginSucceeded() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            loginUserUseCase.execute(request);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_SUCCEEDED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("should publish LOGIN_FAILED on bad credentials")
        void shouldPublishLoginFailed() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());
            doThrow(new AuthenticationFailedException("Bad credentials"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("should still login when the audit store is down")
        void shouldLoginWhenAuditStoreDown() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());
            doThrow(new RuntimeException("audit down")).when(auditEventPort)
                    .publish(any(com.bank.app.common.domain.event.AuditEvent.class));

            var response = loginUserUseCase.execute(request);

            // Availability over audit completeness on the auth path (K11/D4).
            assertThat(response.token()).isEqualTo("mock-jwt-token");
        }

        @Test
        @DisplayName("should publish LOGIN_FAILED when IP is blocked")
        void shouldPublishLoginFailedWhenIpBlocked() {
            // A brute-force block is itself a security event: without this
            // row, post-incident review cannot tell how many accounts were
            // probed before the guard engaged.
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(true);
            when(loginAttemptPort.getWindowMinutes()).thenReturn(15);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(TooManyFailedLoginAttemptsException.class);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
            verifyNoInteractions(authenticationPort);
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("should publish LOGIN_FAILED when username is blocked")
        void shouldPublishLoginFailedWhenUsernameBlocked() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(USERNAME)).thenReturn(true);
            when(loginAttemptPort.getWindowMinutes()).thenReturn(15);
            when(clockProvider.clock()).thenReturn(Clock.systemUTC());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(TooManyFailedLoginAttemptsException.class);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
            verifyNoInteractions(authenticationPort);
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }
    }

}
