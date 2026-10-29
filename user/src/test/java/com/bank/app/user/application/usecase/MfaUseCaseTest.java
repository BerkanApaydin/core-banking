package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.adapter.out.security.AesGcmMfaEncryptor;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.application.service.MfaReauthenticationService;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.user.domain.Totp;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.UserTestFactory;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.InvalidMfaCodeException;
import com.bank.app.user.domain.exception.MfaAlreadyEnabledException;
import com.bank.app.user.domain.exception.MfaNotEnrolledException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MFA use cases")
class MfaUseCaseTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    private static final byte[] FIXED_SECRET =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private MfaSecretPort mfaSecretPort;
    @Mock private AuthenticationPort authenticationPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AuditEventPort auditEventPort;
    @Mock private MfaAttemptPort mfaAttemptPort;
    @Mock private MfaSecretEncryptorPort encryptorPort;

    private final AesGcmMfaEncryptor encryptor = AesGcmMfaEncryptor.ephemeral();
    private final TotpPolicy totpPolicy = new TotpPolicy(30, 6, 1);

    private MfaCodeVerificationService mfaCodeVerification;
    private MfaReauthenticationService mfaReauthentication;
    private EnableMfaUseCaseImpl enable;
    private ConfirmMfaUseCaseImpl confirm;
    private DisableMfaUseCaseImpl disable;

    @BeforeEach
    void setUp() {
        // The verification service persists the accepted TOTP step counter;
        // the mock must accept it (a rejected counter reads as a replay).
        lenient().when(mfaSecretPort.recordAcceptedTotpCounter(any(), anyLong())).thenReturn(true);
        mfaCodeVerification = new MfaCodeVerificationService(mfaSecretPort, encryptor, totpPolicy,
                mfaAttemptPort, clockProvider);
        mfaReauthentication = new MfaReauthenticationService(authenticationPort, mfaAttemptPort,
                auditEventPort, clockProvider);
        enable = new EnableMfaUseCaseImpl(loadUserPort, mfaReauthentication, mfaSecretPort,
                encryptor, "test-issuer", clockProvider, auditEventPort);
        confirm = new ConfirmMfaUseCaseImpl(loadUserPort, saveUserPort, mfaReauthentication,
                refreshTokenPort, mfaSecretPort, mfaCodeVerification, clockProvider, auditEventPort);
        disable = new DisableMfaUseCaseImpl(loadUserPort, saveUserPort, mfaReauthentication,
                refreshTokenPort, mfaSecretPort, mfaCodeVerification, clockProvider, auditEventPort);
    }

    private static User persistedUser(Long id, String username) {
        return User.rehydrate(new UserId(id), username, UserTestFactory.FIXTURE_HASH,
                Role.ROLE_USER, null, null, 0L);
    }

    private String currentCode(byte[] secret) {
        long step = FIXED.millis() / 1000L / 30L;
        return Totp.generateCode(secret, step, 6);
    }

    /**
     * A code that is provably outside the acceptance window: it is taken from
     * a step far ahead and checked against every step the policy would try.
     */
    private String codeOutsideTheWindow(byte[] secret) {
        long step = FIXED.millis() / 1000L / 30L;
        String candidate = Totp.generateCode(secret, step + 1000L, 6);
        for (long windowStep = step - 1; windowStep <= step + 1; windowStep++) {
            assertThat(Totp.generateCode(secret, windowStep, 6)).isNotEqualTo(candidate);
        }
        return candidate;
    }

    @Nested
    @DisplayName("EnableMfaUseCase")
    class Enable {

        @Test
        @DisplayName("should return enrollment material and store the encrypted secret without enabling")
        void shouldEnrollWithoutEnabling() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));

            MfaEnrollment enrollment = enable.execute(7L, "Secret123");

            assertThat(enrollment.base32Secret()).hasSize(32);
            assertThat(enrollment.otpauthUri()).startsWith("otpauth://totp/test-issuer:alice?");
            assertThat(user.isMfaEnabled()).isFalse();
            verify(mfaSecretPort).saveSecret(eq(7L), any(String.class));
            verify(saveUserPort, never()).save(any());
            // R-U08: enrolment has no second factor, so a successful password
            // step-up clears the shared verification budget.
            verify(mfaAttemptPort).reset(7L);
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_ENABLED.equals(event.action())
                            && "alice".equals(event.username())
                            && Long.valueOf(7L).equals(event.actorUserId())));
        }

        @Test
        @DisplayName("should reject an unknown user")
        void shouldRejectUnknownUser() {
            when(loadUserPort.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enable.execute(99L, "Secret123"))
                    .isInstanceOf(UserNotFoundException.class);
            verify(mfaSecretPort, never()).saveSecret(any(), any());
        }

        @Test
        @DisplayName("should carry the configured policy into the provisioning URI")
        void shouldBuildFromPolicy() {
            TotpPolicy policy = new TotpPolicy(60, 8, 2);
            EnableMfaUseCaseImpl policyBound = EnableMfaUseCaseImpl.withPolicy(
                    loadUserPort, mfaReauthentication, mfaSecretPort, encryptorPort,
                    "test-issuer", policy, clockProvider, auditEventPort);
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            when(encryptorPort.encrypt(any())).thenReturn("cipher");

            MfaEnrollment enrollment = policyBound.execute(7L, "Secret123");

            assertThat(enrollment.otpauthUri())
                    .contains("digits=8")
                    .contains("period=60");
            assertThat(enrollment.base32Secret()).hasSize(32);
        }

        @Test
        @DisplayName("should zeroize the plaintext secret after building enrollment material")
        void shouldZeroizePlaintextSecret() {
            EnableMfaUseCaseImpl encryptorBacked = new EnableMfaUseCaseImpl(
                    loadUserPort, mfaReauthentication, mfaSecretPort, encryptorPort,
                    "test-issuer", clockProvider, auditEventPort);
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            ArgumentCaptor<byte[]> plaintext = ArgumentCaptor.forClass(byte[].class);
            when(encryptorPort.encrypt(plaintext.capture())).thenReturn("cipher");

            encryptorBacked.execute(7L, "Secret123");

            assertThat(plaintext.getValue()).isNotEmpty().containsOnly((byte) 0);
        }

        @Test
        @DisplayName("should reject when MFA is already enabled")
        void shouldRejectWhenAlreadyEnabled() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> enable.execute(7L, "Secret123"))
                    .isInstanceOf(MfaAlreadyEnabledException.class);
            verify(mfaSecretPort, never()).saveSecret(any(), any());
        }

        @Test
        @DisplayName("should reject a wrong password without minting a secret")
        void shouldRejectWrongPassword() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "nope"))
                    .thenThrow(new AuthenticationFailedException());

            assertThatThrownBy(() -> enable.execute(7L, "nope"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            verify(mfaSecretPort, never()).saveSecret(any(), any());
            // R-U08: the wrong password is charged to the account budget and
            // audited, not silently discarded.
            verify(mfaAttemptPort).recordFailure(7L);
            verify(mfaAttemptPort, never()).reset(anyLong());
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_REAUTH_FAILED.equals(event.action())
                            && "alice".equals(event.username())
                            && Long.valueOf(7L).equals(event.actorUserId())));
        }

        @Test
        @DisplayName("R-U08: an exhausted shared budget blocks before password work")
        void shouldRejectWhenSharedBudgetExhausted() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(mfaAttemptPort.isBlocked(7L)).thenReturn(true);
            when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> enable.execute(7L, "Secret123"))
                    .isInstanceOf(TooManyFailedMfaAttemptsException.class)
                    .hasMessageContaining("15");
            verify(authenticationPort, never()).authenticate(any(), any());
            verify(mfaSecretPort, never()).saveSecret(any(), any());
        }

        @Test
        @DisplayName("should keep the uniform 400 when the re-auth audit store is down")
        void shouldKeepUniformFailureWhenAuditStoreIsDown() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "nope"))
                    .thenThrow(new AuthenticationFailedException());
            doThrow(new IllegalStateException("audit down"))
                    .when(auditEventPort).publishBestEffort(any());

            assertThatThrownBy(() -> enable.execute(7L, "nope"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            verify(mfaAttemptPort).recordFailure(7L);
            verify(mfaSecretPort, never()).saveSecret(any(), any());
        }
    }

    @Nested
    @DisplayName("ConfirmMfaUseCase")
    class Confirm {

        @Test
        @DisplayName("should enable MFA on a correct code")
        void shouldEnableOnCorrectCode() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));

            confirm.execute(7L, "Secret123", currentCode(secret));

            assertThat(user.isMfaEnabled()).isTrue();
            assertThat(user.getTokenVersion()).isEqualTo(1L);
            verify(saveUserPort).save(user);
            verify(refreshTokenPort).revokeAllForUser(7L);
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_ENABLED.equals(event.action())
                            && event.details().contains("confirmed")
                            && "alice".equals(event.username())
                            && Long.valueOf(7L).equals(event.actorUserId())));
        }

        @Test
        @DisplayName("should reject an unknown user")
        void shouldRejectUnknownUser() {
            when(loadUserPort.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> confirm.execute(99L, "Secret123", "123456"))
                    .isInstanceOf(UserNotFoundException.class);
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject an already-enabled account before the password step-up")
        void shouldRejectAlreadyEnabledBeforePasswordStepUp() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> confirm.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(MfaAlreadyEnabledException.class);

            verify(authenticationPort, never()).authenticate(any(), any());
            verify(mfaSecretPort, never()).findSecret(any());
            verify(saveUserPort, never()).save(any());
            verify(refreshTokenPort, never()).revokeAllForUser(any());
        }

        @Test
        @DisplayName("should reject before the code check when the budget is exhausted mid-request")
        void shouldRejectWhenBlockedAfterPasswordBeforeCode() {
            // The shared budget can be exhausted by a concurrent attempt
            // between re-authentication and the code check: the request must
            // not spend a code comparison or consume the step counter.
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(Totp.generateSecret())));
            when(mfaAttemptPort.isBlocked(7L)).thenReturn(false, true);
            when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> confirm.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(TooManyFailedMfaAttemptsException.class)
                    .hasMessageContaining("15");

            verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(saveUserPort, never()).save(any());
            verify(refreshTokenPort, never()).revokeAllForUser(any());
        }

        @Test
        @DisplayName("should reject a wrong code without enabling")
        void shouldRejectWrongCode() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));

            assertThatThrownBy(() -> confirm.execute(7L, "Secret123", "000000"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            assertThat(user.isMfaEnabled()).isFalse();
            assertThat(user.getTokenVersion()).isZero();
            verify(saveUserPort, never()).save(any());
            verify(refreshTokenPort, never()).revokeAllForUser(any());
        }

        @Test
        @DisplayName("should reject a wrong password before checking the code")
        void shouldRejectWrongPassword() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "nope"))
                    .thenThrow(new AuthenticationFailedException());

            assertThatThrownBy(() -> confirm.execute(7L, "nope", "123456"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            verify(mfaSecretPort, never()).findSecret(any());
            verify(mfaAttemptPort).recordFailure(7L);
            // A correct code path owns the reset; a failed password never does.
            verify(mfaAttemptPort, never()).reset(anyLong());
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_REAUTH_FAILED.equals(event.action())));
        }

        @Test
        @DisplayName("should reject when no enrollment was started")
        void shouldRejectWithoutPendingSecret() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            when(mfaSecretPort.findSecret(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> confirm.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(MfaNotEnrolledException.class);
        }

        @Test
        @DisplayName("should charge a wrong confirmation code to the MFA budget")
        void shouldChargeAWrongConfirmationCodeToTheBudget() {
            // A wrong code on the confirmation endpoint must count against the
            // account-keyed budget, otherwise it is a free oracle for
            // guessing the second factor.
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(FIXED_SECRET)));

            assertThatThrownBy(() -> confirm.execute(7L, "Secret123", codeOutsideTheWindow(FIXED_SECRET)))
                    .isInstanceOf(InvalidMfaCodeException.class);

            verify(mfaAttemptPort).recordFailure(7L);
            verify(mfaAttemptPort, never()).reset(anyLong());
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should not consume the TOTP step counter on confirmation")
        void shouldNotConsumeTheCounterOnConfirmation() {
            // The confirmation does not mint a session. Consuming the step
            // counter here would reject the very next legitimate use of the
            // same code (confirm, then log in, inside one 30-second step) and
            // lock the user out of their own account.
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, false));
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(FIXED_SECRET)));

            confirm.execute(7L, "Secret123", currentCode(FIXED_SECRET));

            assertThat(user.isMfaEnabled()).isTrue();
            verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("DisableMfaUseCase")
    class Disable {

        @Test
        @DisplayName("should disable and delete the secret on valid password and code")
        void shouldDisableOnValidCredentials() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));
            when(authenticationPort.authenticate(eq("alice"), eq("Secret123")))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice",
                            Role.ROLE_USER, 0L, true));

            disable.execute(7L, "Secret123", currentCode(secret));

            assertThat(user.isMfaEnabled()).isFalse();
            assertThat(user.getTokenVersion()).isEqualTo(2L);
            verify(saveUserPort).save(user);
            verify(mfaSecretPort).deleteSecret(7L);
            verify(mfaSecretPort).recordAcceptedTotpCounter(eq(7L), anyLong());
            verify(refreshTokenPort).revokeAllForUser(7L);
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_DISABLED.equals(event.action())
                            && "alice".equals(event.username())
                            && Long.valueOf(7L).equals(event.actorUserId())));
        }

        @Test
        @DisplayName("should reject an unknown user")
        void shouldRejectUnknownUser() {
            when(loadUserPort.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> disable.execute(99L, "Secret123", "123456"))
                    .isInstanceOf(UserNotFoundException.class);
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject when MFA is not enabled")
        void shouldRejectWhenNotEnabled() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(MfaNotEnrolledException.class);
            verify(authenticationPort, never()).authenticate(any(), any());
        }

        @Test
        @DisplayName("should reject a missing secret after the password step-up")
        void shouldRejectWhenSecretMissingAfterPassword() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, true));
            when(mfaSecretPort.findSecret(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(MfaNotEnrolledException.class);

            verify(authenticationPort).authenticate("alice", "Secret123");
            verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(saveUserPort, never()).save(any());
            verify(mfaSecretPort, never()).deleteSecret(any());
            verify(refreshTokenPort, never()).revokeAllForUser(any());
        }

        @Test
        @DisplayName("should reject before the code check when the budget is exhausted mid-request")
        void shouldRejectWhenBlockedAfterPasswordBeforeCode() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(authenticationPort.authenticate("alice", "Secret123"))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, true));
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(Totp.generateSecret())));
            when(mfaAttemptPort.isBlocked(7L)).thenReturn(false, true);
            when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(TooManyFailedMfaAttemptsException.class)
                    .hasMessageContaining("15");

            verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(saveUserPort, never()).save(any());
            verify(mfaSecretPort, never()).deleteSecret(any());
            verify(refreshTokenPort, never()).revokeAllForUser(any());
        }

        @Test
        @DisplayName("should reject a wrong code without disabling")
        void shouldRejectWrongCode() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));
            when(authenticationPort.authenticate(eq("alice"), eq("Secret123")))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice",
                            Role.ROLE_USER, 0L, true));

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", "000000"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            assertThat(user.isMfaEnabled()).isTrue();
            verify(saveUserPort, never()).save(any());
            verify(mfaSecretPort, never()).deleteSecret(any());
        }

        @Test
        @DisplayName("should reject a wrong password like a wrong code")
        void shouldRejectWrongPassword() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(authenticationPort.authenticate(eq("alice"), eq("nope")))
                    .thenThrow(new AuthenticationFailedException());

            assertThatThrownBy(() -> disable.execute(7L, "nope", "123456"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            assertThat(user.isMfaEnabled()).isTrue();
            verify(saveUserPort, never()).save(any());
            verify(mfaAttemptPort).recordFailure(7L);
            verify(mfaAttemptPort, never()).reset(anyLong());
            verify(auditEventPort).publishBestEffort(argThat(event ->
                    AuditEvent.MFA_REAUTH_FAILED.equals(event.action())));
        }

        @Test
        @DisplayName("R-T12: should reject before password verification when the attempt budget is exhausted")
        void shouldRejectWhenAttemptBudgetExhausted() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(mfaAttemptPort.isBlocked(7L)).thenReturn(true);
            when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", "123456"))
                    .isInstanceOf(TooManyFailedMfaAttemptsException.class);

            verify(authenticationPort, never()).authenticate(any(), any());
            verify(mfaAttemptPort, never()).recordFailure(anyLong());
            verify(saveUserPort, never()).save(any());
            verify(mfaSecretPort, never()).deleteSecret(any());
            verify(refreshTokenPort, never()).revokeAllForUser(anyLong());
        }

        @Test
        @DisplayName("should charge a wrong disable code to the MFA budget")
        void shouldChargeAWrongDisableCodeToTheBudget() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(FIXED_SECRET)));
            when(authenticationPort.authenticate(eq("alice"), eq("Secret123")))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, true));

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", codeOutsideTheWindow(FIXED_SECRET)))
                    .isInstanceOf(InvalidMfaCodeException.class);

            verify(mfaAttemptPort).recordFailure(7L);
            verify(mfaAttemptPort, never()).reset(anyLong());
            verify(mfaSecretPort, never()).deleteSecret(any());
        }

        @Test
        @DisplayName("should keep the secret when the disable code was already consumed")
        void shouldKeepTheSecretOnAReplayedDisableCode() {
            // A code already used on the login path is a replay: the removal
            // must fail and the stored secret must survive, otherwise a
            // captured code could strip the second factor.
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(FIXED_SECRET)));
            when(authenticationPort.authenticate(eq("alice"), eq("Secret123")))
                    .thenReturn(new AuthenticationPort.AuthenticatedUser(
                            new UserId(7L), "alice", Role.ROLE_USER, 0L, true));
            // The conditional counter update says "not older": this code was
            // already used inside its validity window.
            when(mfaSecretPort.recordAcceptedTotpCounter(eq(7L), anyLong())).thenReturn(false);

            assertThatThrownBy(() -> disable.execute(7L, "Secret123", currentCode(FIXED_SECRET)))
                    .isInstanceOf(InvalidMfaCodeException.class);

            assertThat(user.isMfaEnabled()).isTrue();
            verify(mfaSecretPort, never()).deleteSecret(any());
            verify(saveUserPort, never()).save(any());
            verify(mfaAttemptPort).recordFailure(7L);
        }
    }

    @Test
    @DisplayName("stored ciphertext must decrypt to the enrolled secret")
    void storedCiphertextMustMatchEnrollment() {
        User user = persistedUser(9L, "bob");
        when(loadUserPort.findById(9L)).thenReturn(Optional.of(user));
        when(clockProvider.clock()).thenReturn(FIXED);
        when(authenticationPort.authenticate("bob", "Secret123"))
                .thenReturn(new AuthenticationPort.AuthenticatedUser(
                        new UserId(9L), "bob", Role.ROLE_USER, 0L, false));

        MfaEnrollment enrollment = enable.execute(9L, "Secret123");

        var ciphertext = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mfaSecretPort).saveSecret(eq(9L), ciphertext.capture());
        byte[] stored = encryptor.decryptFromBase64(ciphertext.getValue());
        assertThat(Totp.base32Encode(stored)).isEqualTo(enrollment.base32Secret());
        assertThat(Totp.verify(stored, currentCode(stored), FIXED, 30, 6, 1)).isTrue();
    }
}
