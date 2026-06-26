package com.bank.app.common.adapter.in.idempotency;

import com.bank.app.common.adapter.in.config.IdempotencyProperties;
import com.bank.app.common.application.port.out.IdempotencyPort;
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

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerEdgeCaseTest {

    @Mock private IdempotencyPort idempotencyPort;

    private IdempotencyCleanupScheduler scheduler;

    private final IdempotencyProperties idempotencyProperties = new IdempotencyProperties(24, "0 0 * * * *");

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(idempotencyPort, idempotencyProperties);
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

        assertThrows(RuntimeException.class, () -> scheduler.cleanupExpiredKeys());
    }

    @Test
    void shouldCalculateCorrectThreshold() {
        IdempotencyCleanupScheduler shortScheduler = new IdempotencyCleanupScheduler(idempotencyPort, new IdempotencyProperties(1, "0 0 * * * *"));

        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(1);

        shortScheduler.cleanupExpiredKeys();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(idempotencyPort).deleteExpired(captor.capture());
        LocalDateTime threshold = captor.getValue();
        assertTrue(threshold.isBefore(LocalDateTime.now()));
        assertTrue(threshold.isAfter(LocalDateTime.now().minusHours(2)));
    }
}
