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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

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

    // timeout=30 mirrors the use-case tx timeout: a stuck handler must not
    // hold a partition row lock forever (K4/D6).
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void processEvent(EventEntry fallbackEvent) {
        EventEntry event = outboxPort.findByIdForUpdateSkipLocked(fallbackEvent.id()).orElse(null);

        if (event == null || event.processed() || event.deadLetter()) {
            return;
        }

        try {
            OutboxEventPort handler = handlers.stream()
                    .filter(h -> h.supports(event.eventType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No handler for event type: " + event.eventType()));

            handler.handle(event);

            outboxPort.markProcessed(event.id());
            afterCommit(() -> {
                count(processedCounter);
                log.info("Successfully processed outbox event id: {}", event.id());
            });
        } catch (Exception e) {
            // Handler messages can contain provider payloads or personal data.
            log.warn("Failed to process outbox event id: {}, failureType={}",
                    event.id(), e.getClass().getName());
            throw new RuntimeException("Outbox event processing failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void recordFailure(EventEntry fallbackEvent, Throwable t, int maxRetries) {
        EventEntry event = outboxPort.findByIdForUpdateSkipLocked(fallbackEvent.id()).orElse(null);

        if (event == null || event.processed() || event.deadLetter()) {
            return;
        }

        int nextRetry = event.retryCount() + 1;
        // Persist a stable diagnostic type, never an untrusted exception message.
        String error = t.getClass().getName();

        if (nextRetry >= maxRetries) {
            outboxPort.markDeadLetter(Objects.requireNonNull(event.id()), error, nextRetry);
            afterCommit(() -> {
                count(deadLetterCounter);
                log.warn("Outbox event moved to dead letter after {} retries. id: {}, failureType={}",
                        maxRetries, event.id(), error);
            });
        } else {
            outboxPort.markFailed(Objects.requireNonNull(event.id()), error, nextRetry);
            afterCommit(() -> {
                count(failedCounter);
                log.warn("Failed to process outbox event id: {} (retry {}/{}), failureType={}",
                        event.id(), nextRetry, maxRetries, error);
            });
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            // Direct invocations in tests and non-proxied callers have no commit
            // callback. Production processing enters through REQUIRES_NEW.
            action.run();
        }
    }

    private static void count(@Nullable Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

}
