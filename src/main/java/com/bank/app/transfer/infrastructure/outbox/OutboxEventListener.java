package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OutboxEventListener {

    private final SpringDataOutboxEventRepo outboxRepo;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.partition-count:0}")
    private int partitionCount;

    public OutboxEventListener(SpringDataOutboxEventRepo outboxRepo, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handleTransferCompleted(TransferCompletedEvent event) {
        try {
            TransferEventPayload payload = new TransferEventPayload(
                event.getTransfer().getId(),
                event.getTransfer().getSenderAccountId(),
                event.getTransfer().getReceiverAccountId(),
                event.getTransfer().getAmount().amount(),
                event.getTransfer().getAmount().currency().name()
            );
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
            outboxEvent.setId(UUID.randomUUID().toString());
            outboxEvent.setAggregateType("Transfer");
            outboxEvent.setAggregateId(String.valueOf(event.getTransfer().getId()));
            outboxEvent.setEventType("TransferCompletedEvent");
            outboxEvent.setPayload(jsonPayload);
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setProcessed(false);
            outboxEvent.setPartition(partitionCount > 0
                    ? Math.floorMod(event.getTransfer().getId(), partitionCount)
                    : 0);
            
            outboxRepo.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save outbox event for transfer completed", e);
        }
    }
    
    public record TransferEventPayload(
        Long transferId,
        Long senderAccountId,
        Long receiverAccountId,
        BigDecimal amount,
        String currency
    ) {}
}
