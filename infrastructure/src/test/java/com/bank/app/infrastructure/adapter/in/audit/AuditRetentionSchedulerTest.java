package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.port.out.AuditRetentionPort;
import com.bank.app.audit.config.AuditProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRetentionScheduler")
class AuditRetentionSchedulerTest {

    @Mock
    private AuditRetentionPort retentionPort;

    @Test
    @DisplayName("should delete rows older than the retention window")
    void shouldDeleteOlderThanRetentionWindow() {
        AuditRetentionScheduler scheduler = AuditRetentionScheduler.forTests(
                retentionPort, new AuditProperties(500, true, 365), AdvisorySchedulerLock.alwaysRun());

        scheduler.retainHistory();

        verify(retentionPort).deleteOlderThan(any());
    }

    @Test
    @DisplayName("should skip when not leader")
    void shouldSkipWhenNotLeader() {
        AdvisorySchedulerLock lock = mock(AdvisorySchedulerLock.class);
        when(lock.runIfLeader(anyString(), any())).thenReturn(false);
        AuditRetentionScheduler scheduler = AuditRetentionScheduler.forTests(
                retentionPort, new AuditProperties(500, true, 365), lock);

        scheduler.retainHistory();

        verify(retentionPort, never()).deleteOlderThan(any());
    }

    @Test
    @DisplayName("should derive the cutoff from the injected clock")
    void shouldUseInjectedClockForCutoff() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
        AuditRetentionScheduler scheduler = new AuditRetentionScheduler(
                retentionPort, new AuditProperties(500, true, 365),
                AdvisorySchedulerLock.alwaysRun(), () -> fixed);

        scheduler.retainHistory();

        // The wall clock would put this years later and delete the wrong tail;
        // the exact cutoff also proves the configured retention window is used.
        verify(retentionPort).deleteOlderThan(LocalDateTime.of(2025, 6, 22, 10, 0));
    }
}
