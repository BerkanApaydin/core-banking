package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private OutboxEventPort handler;

    private OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        outboxProcessor = new OutboxProcessor(outboxPort, List.of(handler));
    }

    private EventEntry event(String id, String eventType) {
        return new EventEntry(id, "transfer", "agg-1", eventType,
                "{\"key\":\"value\"}", 0, false, false, null, 0, LocalDateTime.now());
    }

    @Test
    void shouldProcessEventSuccessfully() throws Exception {
        EventEntry event = event("evt-1", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-1")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);

        outboxProcessor.processEvent(event);

        verify(handler).handle(event);
        verify(outboxPort).markProcessed("evt-1");
    }

    @Test
    void shouldDoNothingWhenEventNotFound() {
        EventEntry fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.processEvent(fallback);

        verifyNoInteractions(handler);
        verify(outboxPort, never()).markProcessed(any());
    }

    @Test
    void shouldIgnoreStaleProcessedEventBeforeHandlingOrRecordingFailure() {
        EventEntry stale = new EventEntry("evt-done", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, true, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-done")).thenReturn(Optional.of(stale));

        outboxProcessor.processEvent(stale);
        outboxProcessor.recordFailure(stale, new RuntimeException("stale failure"), 5);

        verifyNoInteractions(handler);
        verify(outboxPort, never()).markProcessed(any());
        verify(outboxPort, never()).markFailed(any(), any(), anyInt());
        verify(outboxPort, never()).markDeadLetter(any(), any(), anyInt());
    }

    @Test
    void shouldThrowWhenNoHandlerFound() {
        EventEntry event = event("evt-2", "UnknownEventType");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-2")).thenReturn(Optional.of(event));
        when(handler.supports("UnknownEventType")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getMessage().contains("UnknownEventType"));
        verify(outboxPort, never()).markProcessed(any());
    }

    @Test
    void shouldRethrowExceptionWhenHandlerFails() throws Exception {
        EventEntry event = event("evt-3", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-3")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("Handler failed")).when(handler).handle(event);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertEquals("Handler failed", ex.getCause().getMessage());
    }

    @Test
    void shouldRecordFailureAndMoveToDeadLetterAfterMaxRetries() {
        EventEntry event = new EventEntry("evt-4", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 4, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-4")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("fail"), 5);

        verify(outboxPort).markDeadLetter("evt-4", RuntimeException.class.getName(), 5);
    }

    @Test
    void shouldRecordFailureAndRetryWhenUnderMaxRetries() {
        EventEntry event = new EventEntry("evt-5", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 1, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-5")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("transient"), 5);

        verify(outboxPort).markFailed("evt-5", RuntimeException.class.getName(), 2);
    }

    @Test
    void shouldDoNothingOnRecordFailureWhenEventNotFound() {
        EventEntry fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.recordFailure(fallback, new RuntimeException("fail"), 5);

        verify(outboxPort, never()).markProcessed(any());
        verify(outboxPort, never()).markFailed(any(), any(), anyInt());
        verify(outboxPort, never()).markDeadLetter(any(), any(), anyInt());
    }

    @Test
    void shouldNeverPersistLongProviderErrorMessage() {
        EventEntry event = new EventEntry("evt-6", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-6")).thenReturn(Optional.of(event));

        String longMsg = "a".repeat(3000);
        outboxProcessor.recordFailure(event, new RuntimeException(longMsg), 5);

        verify(outboxPort).markFailed("evt-6", RuntimeException.class.getName(), 1);
    }

    @Test
    void shouldHandleNullErrorMessage() {
        EventEntry event = new EventEntry("evt-7", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-7")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException(), 5);

        verify(outboxPort).markFailed("evt-7", RuntimeException.class.getName(), 1);
    }

    @Test
    void shouldCountProcessedFailedAndDeadLetterEvents() throws Exception {
        var registry = new SimpleMeterRegistry();
        var processor = new OutboxProcessor(outboxPort, List.of(handler), registry);

        EventEntry ok = event("evt-8", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-8")).thenReturn(Optional.of(ok));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);
        processor.processEvent(ok);

        EventEntry retry = new EventEntry("evt-9", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 1, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-9")).thenReturn(Optional.of(retry));
        processor.recordFailure(retry, new RuntimeException("transient"), 5);

        EventEntry dead = new EventEntry("evt-10", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 4, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-10")).thenReturn(Optional.of(dead));
        processor.recordFailure(dead, new RuntimeException("poison"), 5);

        assertEquals(1.0, registry.get("outbox.event.processed").counter().count());
        assertEquals(1.0, registry.get("outbox.event.failed").counter().count());
        assertEquals(1.0, registry.get("outbox.event.dead_letter").counter().count());
    }

    @Test
    void shouldPublishOutcomeCountersOnlyAfterCommit() throws Exception {
        var registry = new SimpleMeterRegistry();
        var processor = new OutboxProcessor(outboxPort, List.of(handler), registry);
        EventEntry event = event("evt-commit", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked(event.id())).thenReturn(Optional.of(event));
        when(handler.supports(event.eventType())).thenReturn(true);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            processor.processEvent(event);
            assertEquals(0.0, registry.get("outbox.event.processed").counter().count());

            TransactionSynchronizationUtils.triggerAfterCommit();
            assertEquals(1.0, registry.get("outbox.event.processed").counter().count());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void shouldNotCountDeadLetterWhenTransactionRollsBack() {
        var registry = new SimpleMeterRegistry();
        var processor = new OutboxProcessor(outboxPort, List.of(handler), registry);
        EventEntry event = new EventEntry("evt-rollback", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 4, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked(event.id())).thenReturn(Optional.of(event));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            processor.recordFailure(event, new RuntimeException("transient"), 5);
            // Rollback does not invoke afterCommit. Clear the synchronization
            // exactly as the transaction manager does on completion.
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertEquals(0.0, registry.get("outbox.event.dead_letter").counter().count());
    }
}
