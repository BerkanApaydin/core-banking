package com.bank.app.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventJpaRepository outboxRepo;
    private final List<OutboxEventHandler> handlers;

    public OutboxProcessor(OutboxEventJpaRepository outboxRepo, List<OutboxEventHandler> handlers) {
        this.outboxRepo = outboxRepo;
        this.handlers = handlers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEventJpaEntity fallbackEvent) {
        OutboxEventJpaEntity event = outboxRepo.findByIdForUpdateSkipLocked(fallbackEvent.getId()).orElse(null);

        if (event == null) {
            return;
        }

        try {
            OutboxEventHandler handler = handlers.stream()
                    .filter(h -> h.supports(event.getEventType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No handler for event type: " + event.getEventType()));

            handler.handle(event);

            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            event.setLastError(null);
            outboxRepo.save(event);
            log.info("Successfully processed outbox event id: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process outbox event id: {}. Error: {}", event.getId(), e.getMessage(), e);
            throw new RuntimeException("Outbox event processing failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(OutboxEventJpaEntity fallbackEvent, Throwable t, int maxRetries) {
        OutboxEventJpaEntity event = outboxRepo.findById(fallbackEvent.getId()).orElse(null);

        if (event == null) {
            return;
        }

        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastError(truncate(t.getMessage(), 2000));

        if (nextRetry >= maxRetries) {
            event.setDeadLetter(true);
            log.error("Outbox event moved to dead letter after {} retries. id: {}", maxRetries, event.getId(), t);
        } else {
            log.warn("Failed to process outbox event id: {} (retry {}/{})", event.getId(), nextRetry, maxRetries, t);
        }
        outboxRepo.save(event);
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
