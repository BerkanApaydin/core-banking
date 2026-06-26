package com.bank.app.common.adapter.in.outbox;

import com.bank.app.common.adapter.out.outbox.OutboxEventHandler;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxPort outboxPort;
    private final List<OutboxEventHandler> handlers;

    public OutboxProcessor(OutboxPort outboxPort, List<OutboxEventHandler> handlers) {
        this.outboxPort = outboxPort;
        this.handlers = handlers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(EventEntry fallbackEvent) {
        EventEntry event = outboxPort.findByIdForUpdateSkipLocked(fallbackEvent.id()).orElse(null);

        if (event == null) {
            return;
        }

        try {
            OutboxEventHandler handler = handlers.stream()
                    .filter(h -> h.supports(event.eventType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No handler for event type: " + event.eventType()));

            handler.handle(event);

            outboxPort.markProcessed(event.id());
            log.info("Successfully processed outbox event id: {}", event.id());
        } catch (Exception e) {
            log.warn("Failed to process outbox event id: {}. Error: {}", event.id(), e.getMessage(), e);
            throw new RuntimeException("Outbox event processing failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(EventEntry fallbackEvent, Throwable t, int maxRetries) {
        EventEntry event = outboxPort.findByIdForUpdateSkipLocked(fallbackEvent.id()).orElse(null);

        if (event == null) {
            return;
        }

        int nextRetry = event.retryCount() + 1;
        String error = Optional.ofNullable(t.getMessage())
                .map(msg -> truncate(msg, 2000))
                .orElse(null);

        if (nextRetry >= maxRetries) {
            outboxPort.markDeadLetter(Objects.requireNonNull(event.id()), error, nextRetry);
            log.warn("Outbox event moved to dead letter after {} retries. id: {}", maxRetries, event.id(), t);
        } else {
            outboxPort.markFailed(Objects.requireNonNull(event.id()), error, nextRetry);
            log.warn("Failed to process outbox event id: {} (retry {}/{})", event.id(), nextRetry, maxRetries, t);
        }
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
