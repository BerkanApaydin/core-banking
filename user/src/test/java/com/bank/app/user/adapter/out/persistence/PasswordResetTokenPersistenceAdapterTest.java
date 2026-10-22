package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetTokenPersistenceAdapter")
@SuppressWarnings("null")
class PasswordResetTokenPersistenceAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final String HASH = "a".repeat(64);

    @Mock private PasswordResetTokenJpaRepository repository;

    private PasswordResetTokenPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PasswordResetTokenPersistenceAdapter(repository);
    }

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("persists only the digest and metadata")
        void shouldPersistDigestAndMetadata() {
            adapter.issue(new PasswordResetToken(HASH, 7L, NOW.plusMinutes(15), null));

            ArgumentCaptor<PasswordResetTokenJpaEntity> captor =
                    ArgumentCaptor.forClass(PasswordResetTokenJpaEntity.class);
            verify(repository).save(captor.capture());
            PasswordResetTokenJpaEntity saved = captor.getValue();
            assertThat(saved.getTokenHash()).isEqualTo(HASH);
            assertThat(saved.getUserId()).isEqualTo(7L);
            assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMinutes(15));
            assertThat(saved.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("persists an already-used timestamp when present")
        void shouldPersistUsedAt() {
            LocalDateTime usedAt = NOW.minusMinutes(1);

            adapter.issue(new PasswordResetToken(HASH, 7L, NOW.plusMinutes(15), usedAt));

            ArgumentCaptor<PasswordResetTokenJpaEntity> captor =
                    ArgumentCaptor.forClass(PasswordResetTokenJpaEntity.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getUsedAt()).isEqualTo(usedAt);
        }

        @Test
        @DisplayName("maps a store failure to backend unavailable")
        void shouldMapStoreFailure() {
            when(repository.save(any()))
                    .thenThrow(new DataAccessResourceFailureException("down"));

            assertThatThrownBy(() ->
                    adapter.issue(new PasswordResetToken(HASH, 7L, NOW.plusMinutes(15), null)))
                    .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("findByHash")
    class FindByHash {

        @Test
        @DisplayName("returns empty without querying for a null hash")
        void shouldRejectNullHash() {
            assertThat(adapter.findByHash(null)).isEmpty();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("maps the row back to the domain record")
        void shouldMapRow() {
            PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
            entity.setTokenHash(HASH);
            entity.setUserId(7L);
            entity.setExpiresAt(NOW.plusMinutes(15));
            entity.setUsedAt(NOW.minusMinutes(1));
            when(repository.findById(HASH)).thenReturn(Optional.of(entity));

            Optional<PasswordResetToken> found = adapter.findByHash(HASH);

            assertThat(found).contains(new PasswordResetToken(
                    HASH, 7L, NOW.plusMinutes(15), NOW.minusMinutes(1)));
        }

        @Test
        @DisplayName("returns empty for an unknown hash")
        void shouldReturnEmptyForUnknownHash() {
            when(repository.findById(HASH)).thenReturn(Optional.empty());
            assertThat(adapter.findByHash(HASH)).isEmpty();
        }

        @Test
        @DisplayName("maps a read failure to backend unavailable")
        void shouldMapReadFailure() {
            when(repository.findById(HASH))
                    .thenThrow(new DataAccessResourceFailureException("down"));

            assertThatThrownBy(() -> adapter.findByHash(HASH))
                    .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("tryMarkUsed")
    class TryMarkUsed {

        @Test
        @DisplayName("is true only when the conditional update matched a row")
        void shouldReportWinner() {
            when(repository.markUsedIfUnused(HASH, NOW)).thenReturn(1);
            assertThat(adapter.tryMarkUsed(HASH, NOW)).isTrue();

            when(repository.markUsedIfUnused(HASH, NOW)).thenReturn(0);
            assertThat(adapter.tryMarkUsed(HASH, NOW)).isFalse();
        }

        @Test
        @DisplayName("maps an update failure to backend unavailable")
        void shouldMapUpdateFailure() {
            when(repository.markUsedIfUnused(HASH, NOW))
                    .thenThrow(new DataAccessResourceFailureException("down"));

            assertThatThrownBy(() -> adapter.tryMarkUsed(HASH, NOW))
                    .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserId {

        @Test
        @DisplayName("delegates the bulk revoke")
        void shouldDelegate() {
            adapter.deleteByUserId(7L);
            verify(repository).deleteByUserId(7L);
        }

        @Test
        @DisplayName("maps a delete failure to backend unavailable")
        void shouldMapDeleteFailure() {
            doThrow(new DataAccessResourceFailureException("down"))
                    .when(repository).deleteByUserId(7L);

            assertThatThrownBy(() -> adapter.deleteByUserId(7L))
                    .isExactlyInstanceOf(AuthenticationBackendUnavailableException.class);
        }
    }
}

