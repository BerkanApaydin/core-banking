package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

    @Mock
    private RefreshTokenJpaRepository repository;

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
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setTokenHash("old");
        when(repository.findById("old")).thenReturn(Optional.of(entity));

        adapter.markRotated("old", "new");

        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().isRevoked()).isTrue();
        assertThat(entityCaptor.getValue().getReplacedByHash()).isEqualTo("new");
    }

    @Test
    void shouldRevokeFamily() {
        RefreshTokenJpaEntity first = new RefreshTokenJpaEntity();
        RefreshTokenJpaEntity second = new RefreshTokenJpaEntity();
        when(repository.findByFamilyId("family-1")).thenReturn(List.of(first, second));

        adapter.revokeFamily("family-1");

        assertThat(first.isRevoked()).isTrue();
        assertThat(second.isRevoked()).isTrue();
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void shouldRevokeSingleSession() {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        when(repository.findById("hash-1")).thenReturn(Optional.of(entity));

        adapter.revoke("hash-1");

        assertThat(entity.isRevoked()).isTrue();
        verify(repository).save(entity);
    }

    @Test
    void shouldIgnoreRevokeOfUnknownHash() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        adapter.revoke("nope");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldDeleteExpired() {
        when(repository.deleteExpiredBefore(EXPIRY)).thenReturn(3);

        assertThat(adapter.deleteExpiredBefore(EXPIRY)).isEqualTo(3);
    }

    @Test
    void shouldRejectUnknownHashOnRotate() {
        // Kills the NullReturnVals mutant on the orElseThrow lambda: an
        // unknown hash must fail fast instead of silently returning null.
        when(repository.findById("ghost")).thenReturn(Optional.empty());

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
        assertThatThrownBy(() -> adapter.revokeFamily(null))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.deleteExpiredBefore(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }
}
