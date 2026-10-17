package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.adapter.out.security.AesGcmMfaEncryptor;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MFA use cases")
class MfaUseCaseTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private MfaSecretPort mfaSecretPort;
    @Mock private AuthenticationPort authenticationPort;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AuditEventPort auditEventPort;

    private final AesGcmMfaEncryptor encryptor = AesGcmMfaEncryptor.ephemeral();
    private final TotpPolicy totpPolicy = new TotpPolicy(30, 6, 1);

    private EnableMfaUseCaseImpl enable;
    private ConfirmMfaUseCaseImpl confirm;
    private DisableMfaUseCaseImpl disable;

    @BeforeEach
    void setUp() {
        enable = new EnableMfaUseCaseImpl(loadUserPort, mfaSecretPort, encryptor, "test-issuer",
                clockProvider, auditEventPort);
        confirm = new ConfirmMfaUseCaseImpl(loadUserPort, saveUserPort, mfaSecretPort, encryptor,
                totpPolicy, clockProvider, auditEventPort);
        disable = new DisableMfaUseCaseImpl(loadUserPort, saveUserPort, authenticationPort, mfaSecretPort,
                encryptor, totpPolicy, clockProvider, auditEventPort);
    }

    private static User persistedUser(Long id, String username) {
        User user = UserTestFactory.newUser(username);
        // Simulate a persisted identity for audit logging (id is informational here).
        return user;
    }

    private String currentCode(byte[] secret) {
        long step = FIXED.millis() / 1000L / 30L;
        return Totp.generateCode(secret, step, 6);
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

            MfaEnrollment enrollment = enable.execute(7L);

            assertThat(enrollment.base32Secret()).hasSize(32);
            assertThat(enrollment.otpauthUri()).startsWith("otpauth://totp/test-issuer:alice?");
            assertThat(user.isMfaEnabled()).isFalse();
            verify(mfaSecretPort).saveSecret(eq(7L), any(String.class));
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject when MFA is already enabled")
        void shouldRejectWhenAlreadyEnabled() {
            User user = persistedUser(7L, "alice");
            user.enableMfa();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> enable.execute(7L)).isInstanceOf(MfaAlreadyEnabledException.class);
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
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));

            confirm.execute(7L, currentCode(secret));

            assertThat(user.isMfaEnabled()).isTrue();
            verify(saveUserPort).save(user);
        }

        @Test
        @DisplayName("should reject a wrong code without enabling")
        void shouldRejectWrongCode() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(clockProvider.clock()).thenReturn(FIXED);
            byte[] secret = Totp.generateSecret();
            when(mfaSecretPort.findSecret(7L))
                    .thenReturn(Optional.of(encryptor.encryptToBase64(secret)));

            assertThatThrownBy(() -> confirm.execute(7L, "000000"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            assertThat(user.isMfaEnabled()).isFalse();
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject when no enrollment was started")
        void shouldRejectWithoutPendingSecret() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(mfaSecretPort.findSecret(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> confirm.execute(7L, "123456"))
                    .isInstanceOf(MfaNotEnrolledException.class);
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
            verify(saveUserPort).save(user);
            verify(mfaSecretPort).deleteSecret(7L);
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
            when(authenticationPort.authenticate(eq("alice"), eq("nope")))
                    .thenThrow(new AuthenticationFailedException());

            assertThatThrownBy(() -> disable.execute(7L, "nope", "123456"))
                    .isInstanceOf(InvalidMfaCodeException.class);
            assertThat(user.isMfaEnabled()).isTrue();
            verify(saveUserPort, never()).save(any());
        }
    }

    @Test
    @DisplayName("stored ciphertext must decrypt to the enrolled secret")
    void storedCiphertextMustMatchEnrollment() {
        User user = persistedUser(9L, "bob");
        when(loadUserPort.findById(9L)).thenReturn(Optional.of(user));
        when(clockProvider.clock()).thenReturn(FIXED);

        MfaEnrollment enrollment = enable.execute(9L);

        var ciphertext = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mfaSecretPort).saveSecret(eq(9L), ciphertext.capture());
        byte[] stored = encryptor.decryptFromBase64(ciphertext.getValue());
        assertThat(Totp.base32Encode(stored)).isEqualTo(enrollment.base32Secret());
        assertThat(Totp.verify(stored, currentCode(stored), FIXED, 30, 6, 1)).isTrue();
    }
}
