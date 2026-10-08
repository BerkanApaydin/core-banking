package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.IdempotencyProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    // E-2/Short-10: fixed UTC clock — threshold assertions are deterministic
    // and zone-consistent with the production ClockProviderPort (UTC).
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    @Captor
    private ArgumentCaptor<LocalDateTime> thresholdCaptor;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(idempotencyPort, idempotencyProperties,
                AdvisorySchedulerLock.alwaysRun(),
                () -> FIXED);
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
        assertEquals(LocalDateTime.of(2026, 4, 30, 12, 0), threshold);
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
        assertEquals(LocalDateTime.of(2026, 4, 29, 12, 0), threshold);
    }
}
