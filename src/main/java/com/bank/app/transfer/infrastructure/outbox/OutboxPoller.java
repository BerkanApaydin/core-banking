package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.domain.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final SpringDataOutboxEventRepo outboxRepo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxPoller(SpringDataOutboxEventRepo outboxRepo, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollAndProcessEvents() {
        List<OutboxEventJpaEntity> unprocessedEvents = outboxRepo.findTop10ByProcessedFalseOrderByCreatedAtAsc();
        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} unprocessed outbox events", unprocessedEvents.size());

        for (OutboxEventJpaEntity event : unprocessedEvents) {
            try {
                if ("TransferCompletedEvent".equals(event.getEventType())) {
                    OutboxEventListener.TransferEventPayload payload = objectMapper.readValue(
                        event.getPayload(), 
                        OutboxEventListener.TransferEventPayload.class
                    );
                    
                    Transfer transfer = new Transfer(
                        payload.transferId(),
                        payload.senderAccountId(),
                        payload.receiverAccountId(),
                        new Money(payload.amount(), Money.Currency.valueOf(payload.currency())),
                        TransferStatus.COMPLETED,
                        event.getCreatedAt()
                    );
                    
                    eventPublisher.publishEvent(new AsyncTransferCompletedEvent(transfer));
                    
                    event.setProcessed(true);
                    event.setProcessedAt(LocalDateTime.now());
                    outboxRepo.save(event);
                    log.info("Successfully processed outbox event for transfer ID: {}", payload.transferId());
                }
            } catch (Exception e) {
                log.error("Failed to process outbox event with id: {}", event.getId(), e);
            }
        }
    }
}
