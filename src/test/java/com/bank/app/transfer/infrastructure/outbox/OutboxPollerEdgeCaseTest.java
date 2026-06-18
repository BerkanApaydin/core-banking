package com.bank.app.transfer.infrastructure.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerEdgeCaseTest {

    @Mock private OutboxEventLockRepository lockRepository;
    @Mock private SpringDataOutboxEventRepo outboxRepo;
    @Mock private OutboxEventHandler handler;

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(lockRepository, outboxRepo, List.of(handler));
    }

    @Test
    void shouldDoNothingWhenNoUnprocessedEvents() {
        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(Collections.emptyList());

        poller.pollAndProcessEvents();

        verify(outboxRepo, never()).save(any());
        verifyNoInteractions(handler);
    }

    @Test
    void shouldProcessEventSuccessfully() throws Exception {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("TRANSFER_COMPLETED")).thenReturn(true);

        poller.pollAndProcessEvents();

        assertTrue(event.isProcessed());
        assertNotNull(event.getProcessedAt());
        assertNull(event.getLastError());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldIncrementRetryCountOnFailure() {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("TRANSFER_COMPLETED")).thenReturn(true);
        try {
            doThrow(new RuntimeException("Handler failed"))
                    .when(handler).handle(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        poller.pollAndProcessEvents();

        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getLastError());
        assertFalse(event.isDeadLetter());
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        OutboxPoller pollerWithLowRetries = new OutboxPoller(lockRepository, outboxRepo, List.of(handler));

        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(4);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("TRANSFER_COMPLETED")).thenReturn(true);
        try {
            doThrow(new RuntimeException("Handler failed"))
                    .when(handler).handle(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        pollerWithLowRetries.pollAndProcessEvents();

        assertEquals(5, event.getRetryCount());
        assertTrue(event.isDeadLetter());
    }

    @Test
    void shouldHandleMultipleEvents() throws Exception {
        OutboxEventJpaEntity event1 = new OutboxEventJpaEntity();
        event1.setId(UUID.randomUUID().toString());
        event1.setEventType("TYPE_A");
        event1.setProcessed(false);
        event1.setDeadLetter(false);
        event1.setRetryCount(0);
        event1.setCreatedAt(LocalDateTime.now());

        OutboxEventJpaEntity event2 = new OutboxEventJpaEntity();
        event2.setId(UUID.randomUUID().toString());
        event2.setEventType("TYPE_B");
        event2.setProcessed(false);
        event2.setDeadLetter(false);
        event2.setRetryCount(0);
        event2.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt()))
                .thenReturn(List.of(event1, event2));
        when(handler.supports(anyString())).thenReturn(true);

        poller.pollAndProcessEvents();

        verify(outboxRepo, times(2)).save(any(OutboxEventJpaEntity.class));
    }

    @Test
    void shouldThrowWhenNoHandlerFound() {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("UNKNOWN_TYPE");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("UNKNOWN_TYPE")).thenReturn(false);

        poller.pollAndProcessEvents();

        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("No handler for event type: UNKNOWN_TYPE"));
    }

    @Test
    void shouldTruncateLongErrorMessage() {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("TRANSFER_COMPLETED")).thenReturn(true);
        String longError = "x".repeat(3000);
        try {
            doThrow(new RuntimeException(longError))
                    .when(handler).handle(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        poller.pollAndProcessEvents();

        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().length() <= 2000);
    }

    @Test
    void shouldCreateWithCustomMaxRetries() {
        OutboxPoller pollerWithCustomRetries = new OutboxPoller(lockRepository, outboxRepo, List.of(handler), 3);

        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(2);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));
        when(handler.supports("TRANSFER_COMPLETED")).thenReturn(true);
        try {
            doThrow(new RuntimeException("Handler failed"))
                    .when(handler).handle(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        pollerWithCustomRetries.pollAndProcessEvents();

        assertEquals(3, event.getRetryCount());
        assertTrue(event.isDeadLetter());
    }

    @Test
    void shouldHandleNoHandlersList() {
        OutboxPoller emptyPoller = new OutboxPoller(lockRepository, outboxRepo, Collections.emptyList());

        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType("TRANSFER_COMPLETED");
        event.setProcessed(false);
        event.setDeadLetter(false);
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());

        when(lockRepository.findAndLockUnprocessed(anyInt())).thenReturn(List.of(event));

        emptyPoller.pollAndProcessEvents();

        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("No handler for event type: TRANSFER_COMPLETED"));
    }
}
