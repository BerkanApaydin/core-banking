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

import javax.sql.DataSource;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPort outboxPort;
    private final OutboxProcessor outboxProcessor;
    private final OutboxProperties outboxProperties;
    private final MeterRegistry meterRegistry;
    // Used only to read the Hikari pool size, so the worker count can be
    // capped against the shared connection budget. Nullable: unit tests
    // construct the poller without a DataSource.
    private final DataSource dataSource;
    // Perf-2: partition-local parallel batch workers. SKIP LOCKED selection is
    // already safe for concurrent processing of the same partition, so batch
    // rows fan out to workers instead of processing sequentially (~25 ev/s cap).
    private volatile ExecutorService workers;

    private ScheduledExecutorService executor;

    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties) {
        this(outboxPort, outboxProcessor, outboxProperties, null, null);
    }

    /**
     * Convenience constructor for callers that do not need the metrics
     * registry and the connection pool probe.
     */
    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor,
            OutboxProperties outboxProperties, MeterRegistry meterRegistry) {
        this(outboxPort, outboxProcessor, outboxProperties, meterRegistry, null);
    }

    @Autowired
    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry,
            @Autowired(required = false) @Nullable DataSource dataSource) {
        this.outboxPort = outboxPort;
        this.outboxProcessor = outboxProcessor;
        this.outboxProperties = outboxProperties;
        this.dataSource = dataSource;
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
        // PERF-8: workers per partition (I/O-bound handler work), capped at
        // maxWorkerThreads — not 32. Each worker holds a REQUIRES_NEW transaction
        // (one Hikari connection), and the pool is 20: 32 workers plus poller/HTTP
        // threads saturate Hikari, so recordFailure's connection is skipped and
        // retry/dead-letter accounting silently drops. 12 workers (3x default
        // 2 partitions x 4) leaves headroom for web traffic. Both bounds live
        // in OutboxProperties (audit: previously magic literals here).
        int workerThreads = Math.min(Math.max(threadCount * outboxProperties.workerThreadsPerPartition(), 4),
                outboxProperties.maxWorkerThreads());
        // The worker count alone is not the budget: workers AND web traffic
        // share ONE Hikari pool. Cap the workers at
        // (pool size - web reserve) so a busy outbox can never starve the
        // money path. The old code checked the workers in isolation
        // ("8 < 20"), which ignored that the web path needs the other 12.
        int poolSize = connectionPoolSize();
        if (poolSize > 0) {
            int cap = Math.max(1, poolSize - outboxProperties.webConnectionReserve());
            if (workerThreads > cap) {
                log.warn("Capping outbox workers at {} (pool size {} minus web reserve {}): "
                                + "outbox workers and request threads share one connection pool",
                        cap, poolSize, outboxProperties.webConnectionReserve());
                workerThreads = cap;
            }
        }
        workers = Executors.newFixedThreadPool(workerThreads, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "outbox-worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
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
                    initialJitter(pollDelayMs), pollDelayMs, TimeUnit.MILLISECONDS);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                final int partition = p;
                // C-3: jittered initial delay so N partitions do not stampede
                // the DB with simultaneous SELECT ... FOR UPDATE SKIP LOCKED at boot.
                executor.scheduleWithFixedDelay(
                        () -> processPartitionSafely(partition, batchSize, maxRetries),
                        initialJitter(pollDelayMs), pollDelayMs, TimeUnit.MILLISECONDS);
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
        ExecutorService w = workers;
        if (w != null && !w.isShutdown()) {
            w.shutdown();
            try {
                if (!w.awaitTermination(5, TimeUnit.SECONDS)) {
                    w.shutdownNow();
                }
            } catch (InterruptedException e) {
                w.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long initialJitter(long pollDelayMs) {
        if (pollDelayMs <= 0) return 0;
        return ThreadLocalRandom.current().nextLong(0, pollDelayMs + 1);
    }

    /**
     * The Hikari pool size, or {@code -1} when it cannot be determined
     * (no DataSource, or a non-Hikari pool). Used to keep the outbox worker
     * count inside the pool budget together with the web traffic.
     */
    private int connectionPoolSize() {
        if (dataSource == null) {
            return -1;
        }
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                return hikari.getMaximumPoolSize();
            }
            return -1;
        } catch (RuntimeException e) {
            log.debug("Outbox pool-size probe failed: {}", e.getClass().getSimpleName());
            return -1;
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

        ExecutorService w = workers;
        if (w == null || unprocessedEvents.size() == 1) {
            for (EventEntry event : unprocessedEvents) {
                processOne(event, maxRetries);
            }
            return;
        }
        // Parallel fan-out within the partition; each event owns its row lock
        // (SKIP LOCKED) and its own REQUIRES_NEW processing transaction, so
        // ordering within a batch is intentionally not guaranteed (at-least-once).
        List<Future<?>> futures = new ArrayList<>(unprocessedEvents.size());
        for (EventEntry event : unprocessedEvents) {
            futures.add(w.submit(() -> processOne(event, maxRetries)));
        }
        long deadlineNanos = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(outboxProperties.workerTimeoutSeconds());
        for (Future<?> f : futures) {
            // PERF-8: bounded wait — an untimed get() parks the partition
            // thread for the full tx timeout when a handler hangs. Bound
            // lives in OutboxProperties (audit: previously a magic 30s here).
            //
            // The bound is a BATCH deadline, not a per-future one: with a
            // per-future timeout a batch of 50 hanging handlers stalled the
            // partition thread for ceil(50 / workers) x timeout (~3.5 minutes
            // with the defaults), because every future was given the full
            // timeout again. One deadline for the whole batch bounds the
            // stall to workerTimeoutSeconds regardless of the batch size.
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                f.cancel(true);
                log.warn("Outbox batch deadline exceeded after {}s; remaining tasks cancelled",
                        outboxProperties.workerTimeoutSeconds());
                continue;
            }
            try {
                f.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                f.cancel(true);
                log.warn("Outbox worker timed out after {}s; task cancelled",
                        outboxProperties.workerTimeoutSeconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                log.warn("Outbox worker failed: {}", e.getCause() != null ? e.getCause().getClass().getName() : "?");
            }
        }
    }

    private void processOne(EventEntry event, int maxRetries) {
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
