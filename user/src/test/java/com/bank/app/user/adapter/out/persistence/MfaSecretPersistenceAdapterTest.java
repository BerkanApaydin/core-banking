package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Availability and fail-closed behaviour of the TOTP-secret store.
 *
 * <p>A database problem on these methods decides whether a user sees a
 * controlled 503 or an unhandled 500, and whether a replay counter is
 * accepted. The adapter must translate every {@code DataAccessException},
 * treat missing ids as no-ops, filter blank secrets and propagate the
 * conditional-update outcome unchanged.
 */
@ExtendWith(MockitoExtension.class)
class MfaSecretPersistenceAdapterTest {

    private static final Long USER_ID = 7L;
    private static final String SECRET = "encrypted-secret";

    @Mock
    private UserJpaRepository repository;

    @Mock
    private TransactionBoundaryPort transactionBoundary;

    @InjectMocks
    private MfaSecretPersistenceAdapter adapter;

    private void runRequiresNewSupplier() {
        when(transactionBoundary.executeRequiresNew(any(), anyInt()))
                .thenAnswer(invocation -> invocation
                        .<Supplier<?>>getArgument(0).get());
    }

    @Test
    void shouldSaveSecretThroughDedicatedStatement() {
        adapter.saveSecret(USER_ID, SECRET);

        verify(repository).updateMfaSecret(USER_ID, SECRET);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldWrapSaveFailureAsBackendUnavailable() {
        when(repository.updateMfaSecret(USER_ID, SECRET))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.saveSecret(USER_ID, SECRET))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void shouldReencryptInDedicatedRequiresNewTransaction() {
        runRequiresNewSupplier();

        adapter.reencryptSecret(USER_ID, SECRET);

        verify(transactionBoundary).executeRequiresNew(any(), anyInt());
        verify(repository).updateMfaSecret(USER_ID, SECRET);
    }

    @Test
    void shouldWrapReencryptFailureInsideTransaction() {
        runRequiresNewSupplier();
        when(repository.updateMfaSecret(USER_ID, SECRET))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.reencryptSecret(USER_ID, SECRET))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }

    @Test
    void shouldWrapTransactionBoundaryFailureOnReencrypt() {
        when(transactionBoundary.executeRequiresNew(any(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("tx unavailable"));

        assertThatThrownBy(() -> adapter.reencryptSecret(USER_ID, SECRET))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }

    @Test
    void shouldReturnSecretWhenPresent() {
        when(repository.findMfaSecret(USER_ID)).thenReturn(Optional.of(SECRET));

        assertThat(adapter.findSecret(USER_ID)).contains(SECRET);
    }

    @Test
    void shouldReturnEmptyForNullOrBlankSecret() {
        assertThat(adapter.findSecret(null)).isEmpty();
        verifyNoInteractions(repository);

        when(repository.findMfaSecret(USER_ID)).thenReturn(Optional.of("   "));
        assertThat(adapter.findSecret(USER_ID)).isEmpty();
    }

    @Test
    void shouldWrapSecretLookupFailure() {
        when(repository.findMfaSecret(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.findSecret(USER_ID))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }

    @Test
    void shouldDeleteSecret() {
        adapter.deleteSecret(USER_ID);

        verify(repository).clearMfaSecret(USER_ID);
    }

    @Test
    void shouldWrapDeleteFailure() {
        when(repository.clearMfaSecret(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.deleteSecret(USER_ID))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }

    @Test
    void shouldRejectNullUserIdWhenRecordingCounterWithoutTouchingStore() {
        assertThat(adapter.recordAcceptedTotpCounter(null, 10L)).isFalse();

        verifyNoInteractions(repository, transactionBoundary);
    }

    @Test
    void shouldReturnTrueWhenCounterIsAccepted() {
        runRequiresNewSupplier();
        when(repository.updateMfaLastTotpCounterIfNewer(USER_ID, 10L)).thenReturn(1);

        assertThat(adapter.recordAcceptedTotpCounter(USER_ID, 10L)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenCounterIsAReplay() {
        runRequiresNewSupplier();
        when(repository.updateMfaLastTotpCounterIfNewer(USER_ID, 10L)).thenReturn(0);

        assertThat(adapter.recordAcceptedTotpCounter(USER_ID, 10L)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTransactionBoundaryYieldsNoResult() {
        when(transactionBoundary.executeRequiresNew(any(), anyInt())).thenReturn(null);

        assertThat(adapter.recordAcceptedTotpCounter(USER_ID, 10L)).isFalse();
    }

    @Test
    void shouldWrapCounterUpdateFailure() {
        runRequiresNewSupplier();
        when(repository.updateMfaLastTotpCounterIfNewer(USER_ID, 10L))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.recordAcceptedTotpCounter(USER_ID, 10L))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }

    @Test
    void shouldReturnLastAcceptedCounterWhenPresent() {
        when(repository.findMfaLastTotpCounter(USER_ID)).thenReturn(Optional.of(42L));

        OptionalLong counter = adapter.findLastAcceptedTotpCounter(USER_ID);

        assertThat(counter).hasValue(42L);
    }

    @Test
    void shouldReturnEmptyLastCounterForNullOrMissingUser() {
        assertThat(adapter.findLastAcceptedTotpCounter(null)).isEmpty();
        verifyNoInteractions(repository);

        when(repository.findMfaLastTotpCounter(USER_ID)).thenReturn(Optional.empty());
        assertThat(adapter.findLastAcceptedTotpCounter(USER_ID)).isEmpty();
    }

    @Test
    void shouldWrapLastCounterLookupFailure() {
        when(repository.findMfaLastTotpCounter(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.findLastAcceptedTotpCounter(USER_ID))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
    }
}
