package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.adapter.out.security.AesGcmMfaEncryptor;
import com.bank.app.user.domain.Totp;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.user.application.port.out.AuthenticationPort.AuthenticatedUser;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUserUseCase")
class LoginUserUseCaseImplTest {

    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private PasswordEncoderPort passwordEncoderPort;
    @Mock private IdGeneratorPort idGenerator;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AuditEventPort auditEventPort;
    @Mock private MfaSecretPort mfaSecretPort;
    @Mock private MfaAttemptPort mfaAttemptPort;
    private final AesGcmMfaEncryptor mfaEncryptor = AesGcmMfaEncryptor.ephemeral();
    private final TotpPolicy totpPolicy = new TotpPolicy(30, 6, 1);
    private MfaCodeVerificationService mfaCodeVerification;
    private LoginUserUseCase loginUserUseCase;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "$2a$12$testpasswordhash00000000000000000000001";
    private static final String CLIENT_IP = "192.168.1.1";
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);


    @BeforeEach
    void setUp() {
        // The shared verification service persists the accepted TOTP step
        // counter; the mock must accept it (a rejected counter reads as a
        // replayed code).
        lenient().when(mfaSecretPort.recordAcceptedTotpCounter(any(), anyLong())).thenReturn(true);
        mfaCodeVerification = new MfaCodeVerificationService(mfaSecretPort, mfaEncryptor, totpPolicy,
                mfaAttemptPort, clockProvider);
        loginUserUseCase = new LoginUserUseCaseImpl(authenticationPort, jwtPort, loginAttemptPort,
                refreshTokenPort, passwordEncoderPort, idGenerator, clockProvider, auditEventPort,
                mfaCodeVerification);
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
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(idGenerator.newId()).thenReturn("family-1");

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
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            AuthResponse response = loginUserUseCase.execute(request);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(jwtPort).generateToken(100L, USERNAME, "ROLE_USER", 5L);
            verify(jwtPort).generateRefreshToken(100L, USERNAME, "ROLE_USER", 5L);
        }

        @Test
        @DisplayName("should login successfully with client IP and decay IP login attempts")
        void shouldLoginWithClientIpAndResetAttempts() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(idGenerator.newId()).thenReturn("family-1");

            AuthResponse response = loginUserUseCase.execute(request, CLIENT_IP);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
            verify(loginAttemptPort).recordIpSuccess(CLIENT_IP);
            verify(loginAttemptPort, never()).reset(CLIENT_IP);
            verify(loginAttemptPort).resetByUsername(USERNAME);
            verify(refreshTokenPort).save(anyString(), eq(100L), anyString(), any());
        }

        @Test
        @DisplayName("should login successfully with null client IP")
        void shouldLoginWithNullClientIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            AuthResponse response = loginUserUseCase.execute(request, null);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verify(loginAttemptPort).isUsernameBlocked(eq(USERNAME), any());
            verify(loginAttemptPort).resetByUsername(USERNAME);
            verify(loginAttemptPort, never()).reset(any());
            verify(loginAttemptPort, never()).recordIpSuccess(any());
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

            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(idGenerator.newId()).thenReturn("family-1");
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
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(true);
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
        void shouldNotReturnTokenWhenAttemptStoreFailsDuringDecay() {
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            doThrow(new LoginAttemptStoreUnavailableException(new RuntimeException("Redis down")))
                    .when(loginAttemptPort).recordIpSuccess(CLIENT_IP);

            // Guard update happens before any token is minted: nothing to revoke.
            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD), CLIENT_IP))
                    .isInstanceOf(LoginAttemptStoreUnavailableException.class);
            verify(loginAttemptPort).recordIpSuccess(CLIENT_IP);
            verify(loginAttemptPort, never()).resetByUsername(USERNAME);
            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verify(jwtPort, never()).generateToken(any(), any(), any(), anyLong());
            verify(jwtPort, never()).generateRefreshToken(any(), any(), any(), anyLong());
            verify(refreshTokenPort, never()).save(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should map unknown user to generic 401 (no enumeration)")
        void shouldThrowWhenUserNotFound() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            doThrow(new UserNotFoundException("User not found")).when(authenticationPort).authenticate(USERNAME, PASSWORD);

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(authenticationPort).authenticate(anyString(), anyString());
            // SE-5: unknown-user path burns one dummy BCrypt compare so the
            // timing matches a real password mismatch.
            verify(passwordEncoderPort).encode("login-timing-oracle-dummy");
            verify(passwordEncoderPort).matches(eq(PASSWORD), any());
            // Kills the auditLogin VoidMethodCall mutant on the unknown-user path.
            var captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            // No IP: no failure counting must happen (kills the recordFailure
            // Negate mutant for the null-IP branch).
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("should record failure for unknown user when IP is present")
        void shouldRecordFailureForUnknownUserWithIp() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            doThrow(new UserNotFoundException("User not found")).when(authenticationPort).authenticate(USERNAME, PASSWORD);

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(loginAttemptPort).recordFailure(CLIENT_IP, USERNAME);
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
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
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
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            AuthenticationBackendUnavailableException failure = new AuthenticationBackendUnavailableException(
                    new RuntimeException("Database connection lost"));
            doThrow(failure).when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isSameAs(failure);

            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verifyNoInteractions(jwtPort);
        }

        @Test
        @DisplayName("should mint the timing-oracle dummy hash once for repeated unknown users")
        void shouldCacheDummyHashAcrossUnknownUserLogins() {
            // Minting a full-cost BCrypt hash per unknown username would turn
            // failed logins into a CPU-exhaustion amplifier.
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(passwordEncoderPort.encode("login-timing-oracle-dummy")).thenReturn("$2a$12$dummy");
            doThrow(new UserNotFoundException("User not found"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD)))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD)))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(passwordEncoderPort, times(1)).encode("login-timing-oracle-dummy");
            verify(passwordEncoderPort, times(2)).matches(eq(PASSWORD), eq("$2a$12$dummy"));
        }

        @Test
        @DisplayName("should still reject generically when the dummy comparison itself fails")
        void shouldNotTurnDummyComparisonFailureIntoServerError() {
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(passwordEncoderPort.encode(anyString())).thenReturn("$2a$12$dummy");
            doThrow(new IllegalStateException("encoder misconfigured"))
                    .when(passwordEncoderPort).matches(anyString(), anyString());
            doThrow(new UserNotFoundException("User not found"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(new AuthRequest(USERNAME, PASSWORD)))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(auditEventPort).publish(any(AuditEvent.class));
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

        private ArgumentCaptor<AuditEvent> auditCaptor() {
            return ArgumentCaptor
                    .forClass(AuditEvent.class);
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
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            loginUserUseCase.execute(request);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_SUCCEEDED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
            // Kills the Negate mutant on (clientIp != null): without IP the
            // detail must not mention an IP.
            assertThat(captor.getValue().details()).doesNotContain("from IP");
        }

        @Test
        @DisplayName("should include client IP in success audit when present")
        void shouldIncludeIpInSuccessAudit() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);

            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(false);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            loginUserUseCase.execute(request, CLIENT_IP);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_SUCCEEDED");
            assertThat(captor.getValue().details()).contains("from IP " + CLIENT_IP);
        }

        @Test
        @DisplayName("should publish LOGIN_FAILED on bad credentials")
        void shouldPublishLoginFailed() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            doThrow(new AuthenticationFailedException("Bad credentials"))
                    .when(authenticationPort).authenticate(anyString(), anyString());

            assertThatThrownBy(() -> loginUserUseCase.execute(request))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            assertThat(captor.getValue().username()).isEqualTo(USERNAME);
            assertThat(captor.getValue().actorUserId()).isNull();
        }

        @Test
        @DisplayName("should attribute an MFA failure audit to the authenticated user")
        void shouldAttributeMfaFailureToAuthenticatedUser() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "123456");
            AuthenticatedUser user = new AuthenticatedUser(
                    new UserId(100L), USERNAME, Role.ROLE_USER, 0L, true);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            var captor = auditCaptor();
            verify(auditEventPort).publish(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo("LOGIN_FAILED");
            assertThat(captor.getValue().actorUserId()).isEqualTo(100L);
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
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            doThrow(new RuntimeException("audit down")).when(auditEventPort)
                    .publish(any(AuditEvent.class));

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
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

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
        void shouldPublishLoginFailedWhenUsernameBlocked() {            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            when(loginAttemptPort.isIpBlocked(CLIENT_IP)).thenReturn(false);
            when(loginAttemptPort.isUsernameBlocked(eq(USERNAME), any())).thenReturn(true);
            when(loginAttemptPort.getWindowMinutes()).thenReturn(15);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);

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

    @Nested
    @DisplayName("MFA second factor")
    class MfaSecondFactor {

        private static final Clock MFA_FIXED =
                Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"),
                        ZoneOffset.UTC);

        private AuthenticatedUser mfaUser() {
            return new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER, 0L, true);
        }

        private void stubSuccess() {
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(mfaUser());
        }

        private void stubMinting() {
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(MFA_FIXED);
            when(idGenerator.newId()).thenReturn("family-1");
        }

        private String currentCode(byte[] secret) {
            long step = MFA_FIXED.millis() / 1000L / 30L;
            return Totp.generateCode(secret, step, 6);
        }

        @Test
        @DisplayName("should login with a correct TOTP code")
        void shouldLoginWithCorrectCode() {
            byte[] secret = Totp.generateSecret();
            stubSuccess();
            stubMinting();
            when(mfaSecretPort.findSecret(100L))
                    .thenReturn(Optional.of(mfaEncryptor.encryptToBase64(secret)));
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, currentCode(secret));

            AuthResponse response = loginUserUseCase.execute(request, CLIENT_IP);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
        }

        @Test
        @DisplayName("should reject a missing code with the generic message and record the failure")
        void shouldRejectMissingCode() {
            stubSuccess();
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(loginAttemptPort).recordFailure(CLIENT_IP, USERNAME);
            // A missing code must burn the account-keyed MFA budget too;
            // otherwise the login endpoint is a free probe that never feeds
            // the shared lockout enforced by the management endpoints.
            verify(mfaAttemptPort).recordFailure(100L);
            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should reject a wrong code with the generic message")
        void shouldRejectWrongCode() {
            byte[] secret = Totp.generateSecret();
            stubSuccess();
            when(clockProvider.clock()).thenReturn(MFA_FIXED);
            when(mfaSecretPort.findSecret(100L))
                    .thenReturn(Optional.of(mfaEncryptor.encryptToBase64(secret)));
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "000000");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(loginAttemptPort).recordFailure(CLIENT_IP, USERNAME);
            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should reject a login while the account MFA budget is locked (429)")
        void shouldRejectWhenMfaAccountIsLockedOut() {
            stubSuccess();
            when(mfaAttemptPort.isBlocked(100L)).thenReturn(true);
            when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);
            when(clockProvider.clock()).thenReturn(MFA_FIXED);
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "123456");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(TooManyFailedMfaAttemptsException.class)
                    .hasMessageContaining("15 minutes");

            // A locked-out account must not even reach the code check...
            verifyNoInteractions(mfaSecretPort);
            // ...and the blocked request is not counted as another wrong code.
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
            verify(loginAttemptPort, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("should surface an attempt-store outage as 503, never as a bad code")
        void shouldPropagateAttemptStoreOutageInsteadOfRendering401() {
            byte[] secret = Totp.generateSecret();
            stubSuccess();
            when(clockProvider.clock()).thenReturn(MFA_FIXED);
            when(mfaSecretPort.findSecret(100L))
                    .thenReturn(Optional.of(mfaEncryptor.encryptToBase64(secret)));
            doThrow(new LoginAttemptStoreUnavailableException(new RuntimeException("Redis down")))
                    .when(mfaAttemptPort).recordFailure(100L);
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "000000");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isInstanceOf(LoginAttemptStoreUnavailableException.class);

            // The shared attempt store is the login guard too: an outage must
            // not be double-counted as a credential failure (or rendered 401).
            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should turn an unexpected secret-store failure into the generic 401")
        void shouldFailClosedWhenSecretStoreThrows() {
            stubSuccess();
            when(mfaSecretPort.findSecret(100L)).thenThrow(new IllegalStateException("decrypt failed"));
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "123456");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
            verify(jwtPort, never()).generateRefreshToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should surface a secret-store outage as infrastructure, not a credential failure")
        void shouldPropagateSecretStoreOutageWithoutChargingLoginBudget() {
            stubSuccess();
            AuthenticationBackendUnavailableException failure = new AuthenticationBackendUnavailableException(
                    new RuntimeException("KMS down"));
            when(mfaSecretPort.findSecret(100L)).thenThrow(failure);
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "123456");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isSameAs(failure);

            // An outage must not consume the user's brute-force budget or be
            // rendered as a misleading 401.
            verify(loginAttemptPort, never()).recordFailure(any(), any());
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should fail closed when the secret is missing or undecryptable")
        void shouldFailClosedOnMissingSecret() {
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(mfaUser());
            when(mfaSecretPort.findSecret(100L)).thenReturn(Optional.empty());
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD, null, null, "123456");

            assertThatThrownBy(() -> loginUserUseCase.execute(request, CLIENT_IP))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);

            verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should skip the second factor for non-enrolled users")
        void shouldSkipForNonEnrolledUsers() {
            AuthRequest request = new AuthRequest(USERNAME, PASSWORD);
            AuthenticatedUser user = new AuthenticatedUser(new UserId(100L), USERNAME, Role.ROLE_USER);
            when(authenticationPort.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtPort.generateToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-jwt-token");
            when(jwtPort.generateRefreshToken(100L, USERNAME, "ROLE_USER", 0L)).thenReturn("mock-refresh-token");
            when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
            when(jwtPort.getExpirationMs()).thenReturn(900000L);
            when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
            when(idGenerator.newId()).thenReturn("family-1");

            AuthResponse response = loginUserUseCase.execute(request);

            assertThat(response.token()).isEqualTo("mock-jwt-token");
            verifyNoInteractions(mfaSecretPort);
        }
    }

}
