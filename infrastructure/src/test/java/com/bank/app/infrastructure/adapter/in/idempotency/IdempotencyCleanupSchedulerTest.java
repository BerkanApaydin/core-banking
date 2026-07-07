package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.IdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyCleanupScheduler")
class IdempotencyCleanupSchedulerTest {

    @Mock
    private IdempotencyPort idempotencyPort;

    @Mock
    private IdempotencyProperties idempotencyProperties;

    private IdempotencyCleanupScheduler scheduler;

    @Captor
    private ArgumentCaptor<LocalDateTime> thresholdCaptor;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(idempotencyPort, idempotencyProperties);
    }

    @Test
    @DisplayName("should delete expired keys based on configured expiration hours")
    void shouldDeleteExpiredKeys() {
        when(idempotencyProperties.expirationHours()).thenReturn(24);
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(5);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(thresholdCaptor.capture());
        LocalDateTime threshold = thresholdCaptor.getValue();
        assertNotNull(threshold);
        assertTrue(threshold.isBefore(LocalDateTime.now()));
        assertTrue(threshold.isAfter(LocalDateTime.now().minusHours(25)));
    }

    @Test
    @DisplayName("should handle zero deleted keys")
    void shouldHandleZeroDeletedKeys() {
        when(idempotencyProperties.expirationHours()).thenReturn(24);
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(0);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("should use configured expiration hours for threshold")
    void shouldUseConfiguredExpirationHours() {
        when(idempotencyProperties.expirationHours()).thenReturn(48);
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(3);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(thresholdCaptor.capture());
        LocalDateTime threshold = thresholdCaptor.getValue();
        assertTrue(threshold.isBefore(LocalDateTime.now().minusHours(47)));
        assertTrue(threshold.isAfter(LocalDateTime.now().minusHours(49)));
    }
}
