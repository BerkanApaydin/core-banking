package com.bank.app.transfer.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxLockRepository lockRepository;
    private final OutboxEventJpaRepository outboxRepo;
    private final List<OutboxEventHandler> handlers;
    private final OutboxProcessor outboxProcessor;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries = 5;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${app.outbox.partition-count:0}")
    private int partitionCount;

    @Autowired
    public OutboxPoller(OutboxLockRepository lockRepository,
                        OutboxEventJpaRepository outboxRepo,
                        List<OutboxEventHandler> handlers) {
        this.lockRepository = lockRepository;
        this.outboxRepo = outboxRepo;
        this.handlers = handlers;
        this.outboxProcessor = new OutboxProcessor(outboxRepo, handlers);
    }

    OutboxPoller(OutboxLockRepository lockRepository,
                 OutboxEventJpaRepository outboxRepo,
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
        this.outboxProcessor = new OutboxProcessor(outboxRepo, handlers);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
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
            try {
                outboxProcessor.processEvent(event);
            } catch (Exception e) {
                outboxProcessor.recordFailure(event, e.getCause() != null ? e.getCause() : e, maxRetries);
            }
        }
    }
}
