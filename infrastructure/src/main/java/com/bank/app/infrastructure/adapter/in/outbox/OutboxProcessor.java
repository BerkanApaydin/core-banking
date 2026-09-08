package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
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
    private final List<OutboxEventPort> handlers;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;

    public OutboxProcessor(OutboxPort outboxPort, List<OutboxEventPort> handlers) {
        this(outboxPort, handlers, null);
    }

    @Autowired
    public OutboxProcessor(OutboxPort outboxPort, List<OutboxEventPort> handlers,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.outboxPort = outboxPort;
        this.handlers = handlers;
        if (meterRegistry != null) {
            // Alert hook, e.g.: sum(increase(outbox_event_dead_letter_total[1h])) > 0
            this.processedCounter = Counter.builder("outbox.event.processed")
                    .description("Outbox events successfully processed").register(meterRegistry);
            this.failedCounter = Counter.builder("outbox.event.failed")
                    .description("Outbox event processing failures (will retry)").register(meterRegistry);
            this.deadLetterCounter = Counter.builder("outbox.event.dead_letter")
                    .description("Outbox events moved to dead letter after exhausting retries")
                    .register(meterRegistry);
        } else {
            this.processedCounter = null;
            this.failedCounter = null;
            this.deadLetterCounter = null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(EventEntry fallbackEvent) {
        EventEntry event = outboxPort.findByIdForUpdateSkipLocked(fallbackEvent.id()).orElse(null);

        if (event == null) {
            return;
        }

        try {
            OutboxEventPort handler = handlers.stream()
                    .filter(h -> h.supports(event.eventType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No handler for event type: " + event.eventType()));

            handler.handle(event);

            outboxPort.markProcessed(event.id());
            count(processedCounter);
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
            count(deadLetterCounter);
            log.warn("Outbox event moved to dead letter after {} retries. id: {}", maxRetries, event.id(), t);
        } else {
            outboxPort.markFailed(Objects.requireNonNull(event.id()), error, nextRetry);
            count(failedCounter);
            log.warn("Failed to process outbox event id: {} (retry {}/{})", event.id(), nextRetry, maxRetries, t);
        }
    }

    private static void count(@Nullable Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
