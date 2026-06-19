package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.IdempotencyKeyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerEdgeCaseTest {

    @Mock private IdempotencyKeyJpaRepository repo;

    private IdempotencyCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(repo, 24);
    }

    @Test
    void shouldHandleZeroExpiredKeys() {
        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(0);

        scheduler.cleanupExpiredKeys();

        verify(repo).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldHandleManyExpiredKeys() {
        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(1000);

        scheduler.cleanupExpiredKeys();

        verify(repo).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldHandleRepositoryFailure() {
        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> scheduler.cleanupExpiredKeys());
    }

    @Test
    void shouldCalculateCorrectThreshold() {
        IdempotencyCleanupScheduler shortScheduler = new IdempotencyCleanupScheduler(repo, 1);

        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(1);

        shortScheduler.cleanupExpiredKeys();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteByCreatedAtBefore(captor.capture());
        LocalDateTime threshold = captor.getValue();
        assertTrue(threshold.isBefore(LocalDateTime.now()));
        assertTrue(threshold.isAfter(LocalDateTime.now().minusHours(2)));
    }
}
