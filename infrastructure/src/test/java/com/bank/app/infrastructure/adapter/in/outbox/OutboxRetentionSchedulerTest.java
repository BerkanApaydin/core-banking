package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRetentionSchedulerTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private IdempotencyPort idempotencyPort;

    @Captor
    private ArgumentCaptor<LocalDateTime> cutoffCaptor;

    @Test
    void shouldDeleteProcessedRowsBeforeHandlerKeysWithSameCutoff() {
        var scheduler = new OutboxRetentionScheduler(outboxPort, idempotencyPort,
                new OutboxProperties(5, 50, 2, 2000, 30));
        when(outboxPort.deleteProcessedBefore(any())).thenReturn(12);
        when(idempotencyPort.deleteExpiredHandlerKeys(any())).thenReturn(34);

        scheduler.retainProcessed();

        verify(outboxPort).deleteProcessedBefore(cutoffCaptor.capture());
        verify(idempotencyPort).deleteExpiredHandlerKeys(cutoffCaptor.capture());
        var cutoffs = cutoffCaptor.getAllValues();
        // Same cutoff for both: a dedup key never disappears while its event
        // row may still exist. Roughly 30 days ago (minutes of tolerance for
        // slow CI runners).
        assertThat(cutoffs).hasSize(2);
        assertThat(cutoffs.get(0)).isEqualTo(cutoffs.get(1));
        long daysAgo = ChronoUnit.DAYS.between(cutoffs.get(0), LocalDateTime.now());
        assertThat(daysAgo).isBetween(29L, 30L);
    }

    @Test
    void shouldHonorConfiguredRetentionDays() {
        var scheduler = new OutboxRetentionScheduler(outboxPort, idempotencyPort,
                new OutboxProperties(5, 50, 2, 2000, 7));

        scheduler.retainProcessed();

        verify(outboxPort).deleteProcessedBefore(cutoffCaptor.capture());
        long daysAgo = ChronoUnit.DAYS.between(cutoffCaptor.getValue(), LocalDateTime.now());
        assertThat(daysAgo).isBetween(6L, 7L);
    }
}
