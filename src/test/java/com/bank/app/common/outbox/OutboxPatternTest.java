package com.bank.app.common.outbox;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPatternTest {

    private SpringDataOutboxEventRepo outboxRepo;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    
    private OutboxEventListener eventListener;
    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        outboxRepo = mock(SpringDataOutboxEventRepo.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);
        
        eventListener = new OutboxEventListener(outboxRepo, objectMapper);
        outboxPoller = new OutboxPoller(outboxRepo, objectMapper, eventPublisher);
    }

    @Test
    void shouldSaveOutboxEventWhenTransferCompletedEventFired() throws Exception {
        Transfer transfer = new Transfer(
            123L, 1L, 2L, 
            new Money(new BigDecimal("100.00"), Money.Currency.TRY), 
            TransferStatus.COMPLETED, 
            LocalDateTime.now()
        );
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

        eventListener.handleTransferCompleted(event);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());

        OutboxEventJpaEntity saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("Transfer", saved.getAggregateType());
        assertEquals("123", saved.getAggregateId());
        assertEquals("TransferCompletedEvent", saved.getEventType());
        assertFalse(saved.isProcessed());
        assertNotNull(saved.getPayload());
        
        // Verify payload content
        OutboxEventListener.TransferEventPayload payload = objectMapper.readValue(
            saved.getPayload(), 
            OutboxEventListener.TransferEventPayload.class
        );
        assertEquals(123L, payload.transferId());
        assertEquals(1L, payload.senderAccountId());
        assertEquals(2L, payload.receiverAccountId());
        assertEquals(new BigDecimal("100.00"), payload.amount());
        assertEquals("TRY", payload.currency());
    }

    @Test
    void shouldPollAndProcessUnprocessedEvents() throws Exception {
        OutboxEventListener.TransferEventPayload payload = new OutboxEventListener.TransferEventPayload(
            123L, 1L, 2L, new BigDecimal("100.00"), "TRY"
        );
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
            "event-uuid", "Transfer", "123", "TransferCompletedEvent",
            jsonPayload, LocalDateTime.now(), false, null
        );

        when(outboxRepo.findTop10ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        // Verify that async event was published
        ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        AsyncTransferCompletedEvent asyncEvent = eventCaptor.getValue();
        assertEquals(123L, asyncEvent.getTransfer().getId());
        assertEquals(1L, asyncEvent.getTransfer().getSenderAccountId());
        assertEquals(2L, asyncEvent.getTransfer().getReceiverAccountId());
        assertEquals(new BigDecimal("100.00"), asyncEvent.getTransfer().getAmount().amount());
        assertEquals(Money.Currency.TRY, asyncEvent.getTransfer().getAmount().currency());

        // Verify that the outbox record was marked as processed and saved
        assertTrue(entity.isProcessed());
        assertNotNull(entity.getProcessedAt());
        verify(outboxRepo).save(entity);
    }
}
