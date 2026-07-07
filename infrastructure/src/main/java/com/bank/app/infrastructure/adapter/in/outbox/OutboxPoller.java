package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPort outboxPort;
    private final OutboxProcessor outboxProcessor;
    private final OutboxProperties outboxProperties;

    private ScheduledExecutorService executor;

    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties) {
        this.outboxPort = outboxPort;
        this.outboxProcessor = outboxProcessor;
        this.outboxProperties = outboxProperties;
    }

    @PostConstruct
    public void start() {
        int partitionCount = outboxProperties.partitionCount();
        int batchSize = outboxProperties.batchSize();
        int maxRetries = outboxProperties.maxRetries();
        long pollDelayMs = outboxProperties.pollDelayMs();

        int threadCount = partitionCount <= 0 ? 1 : partitionCount;
        executor = Executors.newScheduledThreadPool(threadCount);

        if (partitionCount <= 0) {
            executor.scheduleWithFixedDelay(
                    () -> processPartitionSafely(-1, batchSize, maxRetries),
                    0, pollDelayMs, TimeUnit.MILLISECONDS);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                final int partition = p;
                executor.scheduleWithFixedDelay(
                        () -> processPartitionSafely(partition, batchSize, maxRetries),
                        0, pollDelayMs, TimeUnit.MILLISECONDS);
            }
        }

        log.info("Outbox poller started with {} thread(s), partitionCount={}, pollDelayMs={}",
                threadCount, partitionCount, pollDelayMs);
    }

    @PreDestroy
    public void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void pollAndProcessEvents() {
        int partitionCount = outboxProperties.partitionCount();
        int batchSize = outboxProperties.batchSize();
        int maxRetries = outboxProperties.maxRetries();
        if (partitionCount <= 0) {
            processPartition(-1, batchSize, maxRetries);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                processPartition(p, batchSize, maxRetries);
            }
        }
    }

    private void processPartitionSafely(int partition, int batchSize, int maxRetries) {
        try {
            processPartition(partition, batchSize, maxRetries);
        } catch (Exception e) {
            log.error("Error processing outbox partition {}: {}", partition, e.getMessage(), e);
        }
    }

    private void processPartition(int partition, int batchSize, int maxRetries) {
        List<EventEntry> unprocessedEvents = outboxPort.findAndLockUnprocessed(batchSize, partition);

        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} unprocessed outbox events for partition {}", unprocessedEvents.size(), partition);

        for (EventEntry event : unprocessedEvents) {
            try {
                outboxProcessor.processEvent(event);
            } catch (Exception e) {
                outboxProcessor.recordFailure(event, e.getCause() != null ? e.getCause() : e, maxRetries);
            }
        }
    }
}
