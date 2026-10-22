package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

    @Mock
    private RefreshTokenJpaRepository repository;

    @Mock
    private UserJpaRepository userRepository;

    @Mock
    private TransactionBoundaryPort transactionBoundary;

    @InjectMocks
    private RefreshTokenPersistenceAdapter adapter;

    @Captor
    private ArgumentCaptor<RefreshTokenJpaEntity> entityCaptor;

    private static final LocalDateTime EXPIRY = LocalDateTime.of(2030, 1, 1, 0, 0);

    @Test
    void shouldSaveUnhashedRecord() {
        adapter.save("hash-1", 7L, "family-1", EXPIRY);

        verify(repository).save(entityCaptor.capture());
        RefreshTokenJpaEntity saved = entityCaptor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo("hash-1");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getFamilyId()).isEqualTo("family-1");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRY);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getReplacedByHash()).isNull();
    }

    @Test
    void shouldFindByHash() {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setTokenHash("hash-1");
        entity.setUserId(7L);
        entity.setFamilyId("family-1");
        entity.setExpiresAt(EXPIRY);
        entity.setRevoked(false);
        when(repository.findById("hash-1")).thenReturn(Optional.of(entity));

        Optional<StoredRefresh> found = adapter.findByTokenHash("hash-1");

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(7L);
        assertThat(found.get().revoked()).isFalse();
    }

    @Test
    void shouldReturnEmptyForNullHash() {
        assertThat(adapter.findByTokenHash(null)).isEmpty();

        verify(repository, never()).findById(any());
    }

    @Test
    void shouldMarkRotated() {
        // C-1: conditional UPDATE wins the race in one round trip.
        when(repository.rotateIfActive("old", "new")).thenReturn(1);

        adapter.markRotated("old", "new");

        verify(repository).rotateIfActive("old", "new");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRevokeFamilyInDedicatedTransactionWhenNoneIsActive() {
        // P-2: single bulk UPDATE instead of SELECT + N UPDATE.
        when(transactionBoundary.executeRequiresNew(any(), anyInt()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<Integer>>getArgument(0).get());
        when(repository.revokeFamilyBulk("family-1")).thenReturn(2);

        adapter.revokeFamily("family-1", 7L);

        // R-T10: the owning user row is locked before the family scan so a
        // concurrent rotation cannot commit a successor behind it.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, repository);
        order.verify(userRepository).findByIdForUpdate(7L);
        order.verify(repository).revokeFamilyBulk("family-1");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void shouldJoinExistingTransactionAndLockUserWhenOneIsActive() {
        // Choosing REQUIRES_NEW inside the refresh transaction would deadlock:
        // this transaction already holds the user-row lock the family revoke
        // needs (R-T10 / K11/D4).
        when(transactionBoundary.isTransactionActive()).thenReturn(true);
        when(repository.revokeFamilyBulk("family-1")).thenReturn(2);

        adapter.revokeFamily("family-1", 7L);

        verify(userRepository).findByIdForUpdate(7L);
        verify(repository).revokeFamilyBulk("family-1");
        verify(transactionBoundary, never()).executeRequiresNew(any(), anyInt());
    }

    @Test
    void shouldRevokeAllSessionsForUser() {
        when(transactionBoundary.executeRequiresNew(any(), anyInt()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<Integer>>getArgument(0).get());
        when(repository.revokeAllForUserBulk(7L)).thenReturn(3);

        adapter.revokeAllForUser(7L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, repository);
        order.verify(userRepository).findByIdForUpdate(7L);
        order.verify(repository).revokeAllForUserBulk(7L);
    }

    @Test
    void shouldJoinExistingTransactionWhenRevokingAllSessions() {
        when(transactionBoundary.isTransactionActive()).thenReturn(true);
        when(repository.revokeAllForUserBulk(7L)).thenReturn(3);

        adapter.revokeAllForUser(7L);

        verify(userRepository).findByIdForUpdate(7L);
        verify(repository).revokeAllForUserBulk(7L);
        verify(transactionBoundary, never()).executeRequiresNew(any(), anyInt());
    }

    @Test
    void shouldRevokeSingleSessionWithConditionalUpdate() {
        when(repository.revokeIfActive("hash-1")).thenReturn(1);

        adapter.revoke("hash-1");

        verify(repository).revokeIfActive("hash-1");
        // R-U09: no read-modify-write, so a concurrent rotateIfActive's
        // replacedByHash link cannot be overwritten.
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldTreatAlreadyRevokedOrUnknownHashAsIdempotentNoOp() {
        when(repository.revokeIfActive("nope")).thenReturn(0);

        adapter.revoke("nope");

        verify(repository).revokeIfActive("nope");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldWrapRevokeDataAccessFailure() {
        when(repository.revokeIfActive("hash-1"))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> adapter.revoke("hash-1"))
                .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void shouldDeleteExpired() {
        when(repository.deleteExpiredBefore(EXPIRY)).thenReturn(3);

        assertThat(adapter.deleteExpiredBefore(EXPIRY)).isEqualTo(3);
    }

    @Test
    void shouldRejectUnknownHashOnRotate() {
        // 0 rotated rows (unknown hash or lost race) must fail fast instead
        // of silently returning — the use case re-reads from there.
        when(repository.rotateIfActive("ghost", "new")).thenReturn(0);

        assertThatThrownBy(() -> adapter.markRotated("ghost", "new"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown refresh token");
    }

    @Test
    void shouldRejectNulls() {
        assertThatThrownBy(() -> adapter.save(null, 7L, "f", EXPIRY))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.markRotated(null, "new"))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.revokeFamily(null, 7L))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.revokeFamily("f", null))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.deleteExpiredBefore(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }
}
