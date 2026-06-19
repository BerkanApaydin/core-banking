package com.bank.app.transfer.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventLockRepository lockRepository;
    private final SpringDataOutboxEventRepo outboxRepo;
    private final List<OutboxEventHandler> handlers;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries = 5;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${app.outbox.partition-count:0}")
    private int partitionCount;

    @Autowired
    public OutboxPoller(OutboxEventLockRepository lockRepository,
                        SpringDataOutboxEventRepo outboxRepo,
                        List<OutboxEventHandler> handlers) {
        this.lockRepository = lockRepository;
        this.outboxRepo = outboxRepo;
        this.handlers = handlers;
    }

    OutboxPoller(OutboxEventLockRepository lockRepository,
                 SpringDataOutboxEventRepo outboxRepo,
                 List<OutboxEventHandler> handlers,
                 @Value("${app.outbox.max-retries:5}") int maxRetries,
                 @Value("${app.outbox.batch-size:50}") int batchSize,
                 @Value("${app.outbox.partition-count:0}") int partitionCount) {
        this.lockRepository = lockRepository;
        this.outboxRepo = outboxRepo;
        this.handlers = handlers;
        this.maxRetries = maxRetries;
        this.batchSize = batchSize;
        this.partitionCount = partitionCount;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
    @Transactional
    public void pollAndProcessEvents() {
        if (partitionCount <= 0) {
            processPartition(-1);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                processPartition(p);
            }
        }
    }

    private void processPartition(int partition) {
        List<OutboxEventJpaEntity> unprocessedEvents = lockRepository.findAndLockUnprocessed(batchSize, partition);
        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} unprocessed outbox events", unprocessedEvents.size());

        for (OutboxEventJpaEntity event : unprocessedEvents) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxEventJpaEntity event) {
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
            int nextRetry = event.getRetryCount() + 1;
            event.setRetryCount(nextRetry);
            event.setLastError(truncate(e.getMessage(), 2000));

            if (nextRetry >= maxRetries) {
                event.setDeadLetter(true);
                log.error("Outbox event moved to dead letter after {} retries. id: {}", maxRetries, event.getId(), e);
            } else {
                log.warn("Failed to process outbox event id: {} (retry {}/{})", event.getId(), nextRetry, maxRetries, e);
            }
            outboxRepo.save(event);
        }
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
