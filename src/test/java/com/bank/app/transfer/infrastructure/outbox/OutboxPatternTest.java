package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class OutboxPatternTest {

    private SpringDataOutboxEventRepo outboxRepo;
    private OutboxEventLockRepository lockRepository;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;

    private OutboxEventListener eventListener;
    private OutboxPoller outboxPoller;
    private TransferCompletedOutboxHandler handler;

    @BeforeEach
    void setUp() {
        outboxRepo = mock(SpringDataOutboxEventRepo.class);
        lockRepository = mock(OutboxEventLockRepository.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);

        eventListener = new OutboxEventListener(outboxRepo, objectMapper);
        handler = new TransferCompletedOutboxHandler(objectMapper, eventPublisher);
        outboxPoller = new OutboxPoller(lockRepository, outboxRepo, List.of(handler));
        ReflectionTestUtils.setField(outboxPoller, "maxRetries", 5);
    }

    @Test
    void shouldSaveOutboxEventWhenTransferCompletedEventFired() throws Exception {
        Transfer transfer = new Transfer(
                123L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

        OutboxEventJpaEntity[] savedEventHolder = new OutboxEventJpaEntity[1];
        when(outboxRepo.save(any(OutboxEventJpaEntity.class))).thenAnswer(invocation -> {
            savedEventHolder[0] = invocation.getArgument(0);
            return savedEventHolder[0];
        });

        eventListener.handleTransferCompleted(event);

        verify(outboxRepo).save(any(OutboxEventJpaEntity.class));

        OutboxEventJpaEntity saved = savedEventHolder[0];
        assertNotNull(saved);
        assertEquals("Transfer", saved.getAggregateType());
        assertEquals("123", saved.getAggregateId());
        assertEquals("TransferCompletedEvent", saved.getEventType());
        assertFalse(saved.isProcessed());
        assertNotNull(saved.getPayload());
    }

    @Test
    void shouldPollAndProcessUnprocessedEvents() throws Exception {
        OutboxEventListener.TransferEventPayload payload = new OutboxEventListener.TransferEventPayload(
                123L, 1L, 2L, new BigDecimal("100.00"), "TRY");
        String jsonPayload = objectMapper.writeValueAsString(payload);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                jsonPayload, LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(10)).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor = ArgumentCaptor
                .forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        AsyncTransferCompletedEvent asyncEvent = eventCaptor.getValue();
        assertEquals(123L, asyncEvent.transfer().getId());
        assertEquals(1L, asyncEvent.transfer().getSenderAccountId());
        assertEquals(2L, asyncEvent.transfer().getReceiverAccountId());

        assertTrue(entity.isProcessed());
        assertNotNull(entity.getProcessedAt());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldIncrementRetryCountWhenProcessingFails() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(10)).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        assertFalse(entity.isProcessed());
        assertEquals(1, entity.getRetryCount());
        assertNotNull(entity.getLastError());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", LocalDateTime.now(), false, null,
                4, false, "previous error");

        when(lockRepository.findAndLockUnprocessed(10)).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertTrue(entity.isDeadLetter());
        assertEquals(5, entity.getRetryCount());
        verify(outboxRepo).save(entity);
    }
}
