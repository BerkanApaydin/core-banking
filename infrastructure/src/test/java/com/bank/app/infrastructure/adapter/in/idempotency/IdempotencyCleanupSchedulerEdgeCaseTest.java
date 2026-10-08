package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.infrastructure.adapter.in.config.IdempotencyProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.common.application.port.out.IdempotencyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerEdgeCaseTest {

    @Mock private IdempotencyPort idempotencyPort;

    private IdempotencyCleanupScheduler scheduler;

    private final IdempotencyProperties idempotencyProperties = new IdempotencyProperties(24, "0 0 * * * *", 3, 500);
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(idempotencyPort, idempotencyProperties,
                AdvisorySchedulerLock.alwaysRun(),
                () -> FIXED);
    }

    @Test
    void shouldHandleZeroExpiredKeys() {
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(0);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(any(LocalDateTime.class));
    }

    @Test
    void shouldHandleManyExpiredKeys() {
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(1000);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(any(LocalDateTime.class));
    }

    @Test
    void shouldHandleRepositoryFailure() {
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduler.cleanupExpiredKeys());
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void shouldCalculateCorrectThreshold() {
        IdempotencyCleanupScheduler shortScheduler = new IdempotencyCleanupScheduler(idempotencyPort, new IdempotencyProperties(1, "0 0 * * * *", 3, 500),
                AdvisorySchedulerLock.alwaysRun(),
                () -> FIXED);

        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(1);

        shortScheduler.cleanupExpiredKeys();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(idempotencyPort).deleteExpired(captor.capture());
        LocalDateTime threshold = captor.getValue();
        assertEquals(LocalDateTime.of(2026, 5, 1, 11, 0), threshold);
    }
}
