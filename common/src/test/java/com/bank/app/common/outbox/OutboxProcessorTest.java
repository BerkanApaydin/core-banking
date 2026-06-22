package com.bank.app.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private OutboxEventHandler handler;

    private OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        outboxProcessor = new OutboxProcessor(outboxRepo, List.of(handler));
    }

    private OutboxEventJpaEntity event(String id, String eventType) {
        return new OutboxEventJpaEntity(id, "transfer", "agg-1", eventType,
                "{\"key\":\"value\"}", LocalDateTime.now(), false, null, 0, false, null, 0);
    }

    @Test
    void shouldProcessEventSuccessfully() throws Exception {
        OutboxEventJpaEntity event = event("evt-1", "TransferCompletedEvent");
        when(outboxRepo.findByIdForUpdateSkipLocked("evt-1")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);

        outboxProcessor.processEvent(event);

        assertTrue(event.isProcessed());
        assertNotNull(event.getProcessedAt());
        assertNull(event.getLastError());
        verify(handler).handle(event);
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldDoNothingWhenEventNotFound() {
        OutboxEventJpaEntity fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxRepo.findByIdForUpdateSkipLocked("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.processEvent(fallback);

        verifyNoInteractions(handler);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoHandlerFound() {
        OutboxEventJpaEntity event = event("evt-2", "UnknownEventType");
        when(outboxRepo.findByIdForUpdateSkipLocked("evt-2")).thenReturn(Optional.of(event));
        when(handler.supports("UnknownEventType")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getMessage().contains("UnknownEventType"));
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void shouldRethrowExceptionWhenHandlerFails() throws Exception {
        OutboxEventJpaEntity event = event("evt-3", "TransferCompletedEvent");
        when(outboxRepo.findByIdForUpdateSkipLocked("evt-3")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("Handler failed")).when(handler).handle(event);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertEquals("Handler failed", ex.getCause().getMessage());
    }

    @Test
    void shouldRecordFailureAndMoveToDeadLetterAfterMaxRetries() {
        OutboxEventJpaEntity event = event("evt-4", "TransferCompletedEvent");
        event.setRetryCount(4);
        when(outboxRepo.findById("evt-4")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("fail"), 5);

        assertEquals(5, event.getRetryCount());
        assertTrue(event.isDeadLetter());
        assertEquals("fail", event.getLastError());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldRecordFailureAndRetryWhenUnderMaxRetries() {
        OutboxEventJpaEntity event = event("evt-5", "TransferCompletedEvent");
        event.setRetryCount(1);
        when(outboxRepo.findById("evt-5")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("transient"), 5);

        assertEquals(2, event.getRetryCount());
        assertFalse(event.isDeadLetter());
        assertEquals("transient", event.getLastError());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldDoNothingOnRecordFailureWhenEventNotFound() {
        OutboxEventJpaEntity fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxRepo.findById("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.recordFailure(fallback, new RuntimeException("fail"), 5);

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void shouldTruncateErrorMessageWhenTooLong() {
        OutboxEventJpaEntity event = event("evt-6", "TransferCompletedEvent");
        when(outboxRepo.findById("evt-6")).thenReturn(Optional.of(event));

        String longMsg = "a".repeat(3000);
        outboxProcessor.recordFailure(event, new RuntimeException(longMsg), 5);

        assertEquals(2000, event.getLastError().length());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldHandleNullErrorMessage() {
        OutboxEventJpaEntity event = event("evt-7", "TransferCompletedEvent");
        when(outboxRepo.findById("evt-7")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException(), 5);

        assertNull(event.getLastError());
        verify(outboxRepo).save(event);
    }
}
