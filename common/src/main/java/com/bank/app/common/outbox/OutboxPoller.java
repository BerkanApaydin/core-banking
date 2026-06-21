package com.bank.app.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxLockRepository lockRepository;
    private final OutboxEventJpaRepository outboxRepo;
    private final OutboxProcessor outboxProcessor;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries = 5;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${app.outbox.partition-count:0}")
    private int partitionCount;

    public OutboxPoller(OutboxLockRepository lockRepository,
                        OutboxEventJpaRepository outboxRepo,
                        OutboxProcessor outboxProcessor) {
        this.lockRepository = lockRepository;
        this.outboxRepo = outboxRepo;
        this.outboxProcessor = outboxProcessor;
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
