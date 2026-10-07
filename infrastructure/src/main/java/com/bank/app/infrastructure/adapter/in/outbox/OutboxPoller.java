package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPort outboxPort;
    private final OutboxProcessor outboxProcessor;
    private final OutboxProperties outboxProperties;
    private final MeterRegistry meterRegistry;

    private ScheduledExecutorService executor;

    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties) {
        this(outboxPort, outboxProcessor, outboxProperties, null);
    }

    @Autowired
    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.outboxPort = outboxPort;
        this.outboxProcessor = outboxProcessor;
        this.outboxProperties = outboxProperties;
        // Nullable like OutboxProcessor's registry: unit tests and non-metered
        // deployments pass null; production alerting keys on the series counted
        // below (see k8s/prometheus-rules.yaml).
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        int partitionCount = outboxProperties.partitionCount();
        int batchSize = outboxProperties.batchSize();
        int maxRetries = outboxProperties.maxRetries();
        long pollDelayMs = outboxProperties.pollDelayMs();

        verifyPendingPartitionsAreCovered(partitionCount);

        int threadCount = partitionCount <= 0 ? 1 : partitionCount;
        executor = Executors.newScheduledThreadPool(threadCount, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "outbox-poller-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });

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
        verifyPendingPartitionsAreCovered(partitionCount);
        if (partitionCount <= 0) {
            processPartition(-1, batchSize, maxRetries);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                processPartition(p, batchSize, maxRetries);
            }
        }
    }

    private void verifyPendingPartitionsAreCovered(int partitionCount) {
        if (partitionCount <= 0) {
            // The unpartitioned poller explicitly selects every partition.
            return;
        }
        long pending = outboxPort.countPendingOutsidePartitionRange(partitionCount);
        if (pending > 0) {
            throw new IllegalStateException("Outbox partitionCount=" + partitionCount + " leaves " + pending
                    + " pending event(s) outside the polling range; restore the previous count and drain them "
                    + "before changing OUTBOX_PARTITION_COUNT");
        }
    }

    private void processPartitionSafely(int partition, int batchSize, int maxRetries) {
        try {
            processPartition(partition, batchSize, maxRetries);
        } catch (Throwable e) {
            // Catch Throwable (not just Exception): an Error must not silently
            // cancel this partition's future scheduled runs. Counted separately
            // from handler failures (outbox.event.failed): this series means
            // the poll cycle itself aborted — selection, locking or dispatch.
            countPartitionError(partition);
            log.error("Error processing outbox partition {}, failureType={}",
                    partition, e.getClass().getName());
        }
    }

    private void countPartitionError(int partition) {
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.poll.partition_error",
                    "partition", String.valueOf(partition)).increment();
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
            } catch (Throwable e) {
                // Catch Throwable (not just Exception): an Error must still
                // advance retryCount/dead-letter and must not skip the rest of
                // the batch. processPartitionSafely guards the cycle itself.
                // Unwrap one level like before so the stored failureType names
                // the handler's root cause, not the processor wrapper.
                Throwable root = e.getCause() != null ? e.getCause() : e;
                outboxProcessor.recordFailure(event, root, maxRetries);
            }
        }
    }
}
