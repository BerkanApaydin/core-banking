package com.bank.app.user.application.service;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.domain.TotpPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the three guarantees the login, enrollment-confirmation and MFA-removal
 * paths share: the account-keyed brute-force bound, the consumed-counter
 * replay protection (V56) and the uniform failure contract. The replay path
 * is the one no integration test can reach deterministically, so it is pinned
 * here: the mocked conditional update returning {@code false} IS the replay
 * signal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MfaCodeVerificationService")
class MfaCodeVerificationServiceTest {

    private static final Long USER_ID = 100L;
    private static final String CODE = "123456";
    private static final String CIPHERTEXT = "encrypted-secret";
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock private MfaSecretPort mfaSecretPort;
    @Mock private MfaSecretEncryptorPort encryptor;
    @Mock private TotpPolicy totpPolicy;
    @Mock private MfaAttemptPort mfaAttemptPort;
    @Mock private ClockProviderPort clockProvider;

    private MfaCodeVerificationService service;

    @BeforeEach
    void setUp() {
        service = new MfaCodeVerificationService(mfaSecretPort, encryptor, totpPolicy,
                mfaAttemptPort, clockProvider);
    }

    private void stubVerifiableCode() {
        when(mfaSecretPort.findSecret(USER_ID)).thenReturn(Optional.of(CIPHERTEXT));
        when(encryptor.decrypt(CIPHERTEXT)).thenReturn(new byte[]{1, 2, 3});
        when(clockProvider.clock()).thenReturn(FIXED);
        when(totpPolicy.verifyCounter(any(), eq(CODE), eq(FIXED)))
                .thenReturn(OptionalLong.of(42L));
    }

    @Test
    @DisplayName("should consume the step counter and reset the failure budget on success")
    void shouldConsumeTheCounterOnSuccess() {
        stubVerifiableCode();
        when(encryptor.isCurrentKey(CIPHERTEXT)).thenReturn(true);
        when(mfaSecretPort.recordAcceptedTotpCounter(USER_ID, 42L)).thenReturn(true);

        assertThat(service.verifyAndConsume(USER_ID, CODE)).isTrue();

        verify(mfaSecretPort).recordAcceptedTotpCounter(USER_ID, 42L);
        verify(mfaAttemptPort).reset(USER_ID);
        verify(mfaAttemptPort, never()).recordFailure(anyLong());
    }

    @Test
    @DisplayName("should reject a replayed code and count it as a failed attempt")
    void shouldRejectReplayedCodeAsFailure() {
        stubVerifiableCode();
        // The conditional update says "the stored counter was not older":
        // this exact code was already used inside its validity window.
        when(mfaSecretPort.recordAcceptedTotpCounter(USER_ID, 42L)).thenReturn(false);

        assertThat(service.verifyAndConsume(USER_ID, CODE)).isFalse();

        verify(mfaAttemptPort).recordFailure(USER_ID);
        verify(mfaAttemptPort, never()).reset(anyLong());
    }

    @Test
    @DisplayName("should not consume the counter on the management endpoints")
    void shouldNotConsumeOnManagementPaths() {
        stubVerifiableCode();
        when(encryptor.isCurrentKey(CIPHERTEXT)).thenReturn(true);

        assertThat(service.verify(USER_ID, CODE)).isTrue();

        verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
        verify(mfaAttemptPort).reset(USER_ID);
    }

    @Test
    @DisplayName("should lazily re-encrypt a secret written with a previous key after a valid check")
    void shouldReencryptStaleSecretAfterSuccessfulVerification() {
        stubVerifiableCode();
        when(encryptor.isCurrentKey(CIPHERTEXT)).thenReturn(false);
        when(encryptor.encrypt(any())).thenReturn("reencrypted-secret");

        assertThat(service.verify(USER_ID, CODE)).isTrue();

        verify(mfaSecretPort).reencryptSecret(USER_ID, "reencrypted-secret");
    }

    @Test
    @DisplayName("should not fail a valid check when lazy re-encryption fails")
    void shouldSwallowReencryptionFailure() {
        stubVerifiableCode();
        when(encryptor.isCurrentKey(CIPHERTEXT)).thenReturn(false);
        when(encryptor.encrypt(any())).thenReturn("reencrypted-secret");
        doThrow(new IllegalStateException("secret store down"))
                .when(mfaSecretPort).reencryptSecret(USER_ID, "reencrypted-secret");

        assertThat(service.verify(USER_ID, CODE)).isTrue();

        verify(mfaAttemptPort).reset(USER_ID);
    }

    @Test
    @DisplayName("should count a blank code as a failed attempt without touching the secret")
    void shouldRejectBlankCodeAsFailure() {
        assertThat(service.verifyAndConsume(USER_ID, "  ")).isFalse();

        verify(mfaAttemptPort).recordFailure(USER_ID);
        verifyNoInteractions(mfaSecretPort);
    }

    @Test
    @DisplayName("should fail closed and count a missing secret as a failed attempt")
    void shouldRejectMissingSecretAsFailure() {
        when(mfaSecretPort.findSecret(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.verifyAndConsume(USER_ID, CODE)).isFalse();

        verify(mfaAttemptPort).recordFailure(USER_ID);
        verify(mfaSecretPort, never()).recordAcceptedTotpCounter(any(), anyLong());
    }

    @Test
    @DisplayName("should fail closed on a decrypt failure but propagate a store outage")
    void shouldDistinguishDecryptFailureFromStoreOutage() {
        when(mfaSecretPort.findSecret(USER_ID)).thenReturn(Optional.of(CIPHERTEXT));
        when(encryptor.decrypt(CIPHERTEXT)).thenThrow(new IllegalStateException("wrong rotation key"));

        assertThat(service.verifyAndConsume(USER_ID, CODE)).isFalse();
        verify(mfaAttemptPort).recordFailure(USER_ID);

        AuthenticationBackendUnavailableException outage =
                new AuthenticationBackendUnavailableException(new RuntimeException("KMS down"));
        // doThrow: encryptor.decrypt already throws, so a when(...) re-stub
        // would surface the previous exception while recording the new one.
        doThrow(outage).when(encryptor).decrypt(CIPHERTEXT);

        assertThatThrownBy(() -> service.verifyAndConsume(USER_ID, CODE)).isSameAs(outage);
        verify(mfaAttemptPort, never()).reset(anyLong());
    }

    @Test
    @DisplayName("should delegate the lockout state and window without touching the secret store")
    void shouldDelegateLockoutState() {
        when(mfaAttemptPort.isBlocked(USER_ID)).thenReturn(true);
        when(mfaAttemptPort.getWindowMinutes()).thenReturn(15);

        assertThat(service.isBlocked(USER_ID)).isTrue();
        assertThat(service.isBlocked(null)).isFalse();
        assertThat(service.getWindowMinutes()).isEqualTo(15);
        verifyNoInteractions(mfaSecretPort);
    }

    @Test
    @DisplayName("should reject a null user id without counting anything")
    void shouldRejectNullUserId() {
        assertThat(service.verifyAndConsume(null, CODE)).isFalse();

        verifyNoInteractions(mfaAttemptPort, mfaSecretPort);
    }
}
