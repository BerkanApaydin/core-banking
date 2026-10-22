package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    // Keeps the periodic task queued long enough to execute it synchronously.
    private static final long LONG_POLL_DELAY_MS = Long.MAX_VALUE / 2;

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private OutboxProcessor outboxProcessor;

    private OutboxPoller outboxPoller;

    private final OutboxProperties outboxProperties = new OutboxProperties(5, 50, 0, 2000, 30, 4, 12, 30);

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, outboxProperties);
    }

    private EventEntry event(String id) {
        return new EventEntry(id, "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
    }

    @Test
    void shouldPollAndProcessEvents() {
        List<EventEntry> events = List.of(event("e1"), event("e2"));
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(events);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(events.get(0));
        verify(outboxProcessor).processEvent(events.get(1));
    }

    @Test
    void shouldDoNothingWhenNoUnprocessedEvents() {
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of());

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, never()).processEvent(any());
    }

    @Test
    void shouldRecordFailureWhenProcessingFails() {
        EventEntry event = event("e-fail");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event));
        doThrow(new RuntimeException("process failed")).when(outboxProcessor).processEvent(event);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).recordFailure(eq(event), any(Throwable.class), eq(5));
    }

    @Test
    void shouldClassifyFailureByRootCauseNotByTheProcessorWrapper() {
        // OutboxProcessor wraps handler failures in a plain RuntimeException.
        // Recording that wrapper would persist "java.lang.RuntimeException"
        // and lose the retry/dead-letter classification of the real cause.
        EventEntry event = event("wrapped");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event));
        OptimisticLockingFailureException root =
                new OptimisticLockingFailureException("version conflict");
        doThrow(new RuntimeException("Outbox event processing failed", root))
                .when(outboxProcessor).processEvent(event);

        outboxPoller.pollAndProcessEvents();

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(outboxProcessor).recordFailure(eq(event), failure.capture(), eq(5));
        assertSame(root, failure.getValue());
    }

    @Test
    void shouldProcessEachPartitionWhenPartitionCountIsPositive() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 3, 2000, 30, 4, 12, 30));
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of(event("e0")));
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));
        when(outboxPort.findAndLockUnprocessed(50, 2)).thenReturn(List.of(event("e2")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(3)).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldSkipEmptyPartitions() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of());
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(1)).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldRefuseStartupWhenPendingEventsUseUnpolledPartitions() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(2)).thenReturn(3L);

        IllegalStateException failure = assertThrows(IllegalStateException.class, outboxPoller::start);

        assertTrue(failure.getMessage().contains("3 pending event(s)"));
        assertTrue(failure.getMessage().contains("OUTBOX_PARTITION_COUNT"));
        verify(outboxPort, never()).findAndLockUnprocessed(anyInt(), anyInt());
        verifyNoInteractions(outboxProcessor);
    }

    @Test
    void shouldRefuseManualPollingWhenPendingEventsUseUnpolledPartitions() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(2)).thenReturn(1L);

        assertThrows(IllegalStateException.class, outboxPoller::pollAndProcessEvents);

        verify(outboxPort, never()).findAndLockUnprocessed(anyInt(), anyInt());
        verifyNoInteractions(outboxProcessor);
    }

    @Test
    void shouldContinueProcessingRemainingEventsWhenOneFails() {
        EventEntry event1 = event("e1");
        EventEntry event2 = event("e2");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("fail")).when(outboxProcessor).processEvent(event1);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(event1);
        verify(outboxProcessor).recordFailure(eq(event1), any(Throwable.class), eq(5));
        verify(outboxProcessor).processEvent(event2);
    }

    @Test
    void shouldShutDownExecutorOnStop() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.isShutdown()).thenReturn(false);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);
        setField("executor", executor);

        outboxPoller.stop();

        verify(executor).shutdown();
        verify(executor, never()).shutdownNow();
    }

    @Test
    void shouldTolerateStopWithoutStart() {
        assertDoesNotThrow(outboxPoller::stop);
    }

    @Test
    void shouldFanOutBatchAcrossWorkersWhenPoolAvailable() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        List<EventEntry> events = List.of(event("w1"), event("w2"), event("w3"));
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(events);
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of());
        ExecutorService workers =
                Executors.newFixedThreadPool(2);
        try {
            var field = OutboxPoller.class.getDeclaredField("workers");
            field.setAccessible(true);
            field.set(outboxPoller, workers);

            // Parallel path joins every future before returning, so verification
            // right after the call is deterministic (no awaits needed).
            outboxPoller.pollAndProcessEvents();
        } finally {
            workers.shutdownNow();
        }

        verify(outboxProcessor).processEvent(events.get(0));
        verify(outboxProcessor).processEvent(events.get(1));
        verify(outboxProcessor).processEvent(events.get(2));
    }

    @Test
    void shouldRecordFailureFromWorkerThreadWithoutSkippingBatch() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 1, 2000, 30, 4, 12, 30));
        EventEntry ok = event("ok");
        EventEntry bad = event("bad");
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of(ok, bad));
        doThrow(new RuntimeException("handler failed")).when(outboxProcessor).processEvent(bad);
        ExecutorService workers =
                Executors.newFixedThreadPool(2);
        try {
            var field = OutboxPoller.class.getDeclaredField("workers");
            field.setAccessible(true);
            field.set(outboxPoller, workers);

            outboxPoller.pollAndProcessEvents();
        } finally {
            workers.shutdownNow();
        }

        verify(outboxProcessor).processEvent(ok);
        verify(outboxProcessor).recordFailure(eq(bad), any(Throwable.class), eq(5));
    }

    @Test
    void shouldRecordTimeoutFailureForRunningAndQueuedEvents() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 1, 2000, 30, 4, 12, 1));
        EventEntry running = event("running");
        EventEntry queued = event("queued");
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of(running, queued));
        CountDownLatch release = new CountDownLatch(1);
        try {
            // Ignores interrupts on purpose: this is the tricky case where
            // Future.cancel(true) cannot stop the handler, yet the queued
            // event behind it never starts and must still reach a retry.
            doAnswer(invocation -> {
                while (release.getCount() > 0) {
                    try {
                        release.await(50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                        // keep waiting; the poller owns the deadline
                    }
                }
                return null;
            }).when(outboxProcessor).processEvent(running);
            ExecutorService workers = Executors.newFixedThreadPool(1);
            try {
                var field = OutboxPoller.class.getDeclaredField("workers");
                field.setAccessible(true);
                field.set(outboxPoller, workers);

                outboxPoller.pollAndProcessEvents();
            } finally {
                release.countDown();
                workers.shutdownNow();
            }
        } finally {
            release.countDown();
        }

        verify(outboxProcessor).recordFailure(eq(running),
                any(OutboxWorkerTimeoutException.class), eq(5));
        verify(outboxProcessor).recordFailure(eq(queued),
                any(OutboxWorkerTimeoutException.class), eq(5));
    }

    @Test
    void shouldReturnZeroJitterForNonPositiveDelay() throws Exception {
        var method = OutboxPoller.class.getDeclaredMethod("initialJitter", long.class);
        method.setAccessible(true);

        assertEquals(0L, (long) method.invoke(null, 0L));
        assertEquals(0L, (long) method.invoke(null, -5L));
    }

    @Test
    void shouldJitterWithinDelayBounds() throws Exception {
        var method = OutboxPoller.class.getDeclaredMethod("initialJitter", long.class);
        method.setAccessible(true);

        for (int i = 0; i < 20; i++) {
            long jitter = (long) method.invoke(null, 1L);
            assertTrue(jitter == 0L || jitter == 1L);
        }
        // delay=1 has a two-value range: a mutant replacing +1 with -1 would
        // collapse it to a single value (always 0).
        boolean sawOne = false;
        for (int i = 0; i < 200; i++) {
            if ((long) method.invoke(null, 1L) == 1L) {
                sawOne = true;
                break;
            }
        }
        assertTrue(sawOne, "expected the delay=1 range to include 1");
        // Primitive-returns mutant (always 0) fails this statistical check.
        boolean sawNonZero = false;
        for (int i = 0; i < 100; i++) {
            if ((long) method.invoke(null, 1_000L) != 0L) {
                sawNonZero = true;
                break;
            }
        }
        assertTrue(sawNonZero, "expected some non-zero jitter over 100 draws");
        for (int i = 0; i < 50; i++) {
            long jitter = (long) method.invoke(null, 2_000L);
            assertTrue(jitter >= 0L && jitter <= 2_000L);
        }
    }

    @Test
    void shouldIgnorePendingOutsideRangeWhenUnpartitioned() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 0, 2000, 30, 4, 12, 30));
        // Unpartitioned mode never queries the pending-range guard; lenient so the
        // boundary mutant (<=0 -> <0, which would query and throw on 5L) still fails.
        lenient().when(outboxPort.countPendingOutsidePartitionRange(0)).thenReturn(5L);
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event("e1")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldProcessExactlyPartitionCountPartitions() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(2)).thenReturn(0L);
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of(event("e0")));
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxPort, times(1)).findAndLockUnprocessed(50, 0);
        verify(outboxPort, times(1)).findAndLockUnprocessed(50, 1);
        verify(outboxPort, never()).findAndLockUnprocessed(50, 2);
        verify(outboxProcessor, times(2)).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldStartPartitionedPollerAndStop() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(2)).thenReturn(0L);

        outboxPoller.start();
        try {
            var executor = (ScheduledExecutorService) field("executor");
            assertNotNull(executor);
            assertFalse(executor.isShutdown());
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldSizeWorkerPoolAtTwelveForThreePartitions() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 3, 2000, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(3)).thenReturn(0L);

        outboxPoller.start();
        try {
            var workers = (ThreadPoolExecutor) field("workers");
            assertEquals(12, workers.getCorePoolSize());
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldCountAbortedPollCyclesPerPartition() throws Exception {
        var registry = new SimpleMeterRegistry();
        // A huge delay keeps the periodic task queued so the test can run it
        // synchronously: no wall-clock race, and a missing schedule fails fast
        // instead of waiting (which PIT would report as TIMED_OUT).
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 0, LONG_POLL_DELAY_MS, 30, 4, 12, 30), registry);
        when(outboxPort.findAndLockUnprocessed(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("database unavailable"));

        outboxPoller.start();
        try {
            assertEquals(1, queuedScheduledTaskCount());
            runQueuedScheduledTasks();
        } finally {
            forceStopRealExecutors();
        }

        assertEquals(1.0,
                registry.counter("outbox.poll.partition_error", "partition", "-1").count(), 0.0);
    }

    @Test
    void shouldReportUnknownConnectionPoolForNullOrNonHikariDataSource() throws Exception {
        assertEquals(-1, connectionPoolSize(null));
        assertEquals(-1, connectionPoolSize(mock(DataSource.class)));
    }

    @Test
    void shouldReadHikariPoolSizeAndFallBackWhenTheProbeFails() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(hikari.getMaximumPoolSize()).thenReturn(17);
        assertEquals(17, connectionPoolSize(hikari));

        HikariDataSource failing = mock(HikariDataSource.class);
        when(failing.getMaximumPoolSize()).thenThrow(new IllegalStateException("probe failed"));
        assertEquals(-1, connectionPoolSize(failing));
    }

    @Test
    void shouldCapWorkersToHikariPoolMinusWebReserve() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(hikari.getMaximumPoolSize()).thenReturn(5);
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 1, 2000, 30, 4, 12, 30, 5, 300, 2), null, hikari);
        when(outboxPort.countPendingOutsidePartitionRange(1)).thenReturn(0L);

        outboxPoller.start();
        try {
            assertEquals(3, ((ThreadPoolExecutor) field("workers")).getCorePoolSize());
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldNotApplyThePoolCapWhenTheProbeReportsAnEmptyPool() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(hikari.getMaximumPoolSize()).thenReturn(0);
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 1, 2000, 30, 4, 12, 30, 5, 300, 2), null, hikari);
        when(outboxPort.countPendingOutsidePartitionRange(1)).thenReturn(0L);

        outboxPoller.start();
        try {
            // poolSize == 0 means "unknown": the configured worker count (4)
            // must be left alone instead of being capped to 1.
            assertEquals(4, ((ThreadPoolExecutor) field("workers")).getCorePoolSize());
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldStartUnpartitionedPollerWithOneThreadAndPollMinusOne() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 0, LONG_POLL_DELAY_MS, 30, 4, 12, 30));
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of());

        outboxPoller.start();
        try {
            assertEquals(1, ((ScheduledThreadPoolExecutor) field("executor")).getCorePoolSize());
            assertEquals(1, queuedScheduledTaskCount());
            runQueuedScheduledTasks();
        } finally {
            forceStopRealExecutors();
        }

        verify(outboxPort, times(1)).findAndLockUnprocessed(50, -1);
        verify(outboxPort, never()).findAndLockUnprocessed(50, 0);
    }

    @Test
    void shouldScheduleEveryConfiguredPartitionAndNoMore() throws Exception {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor,
                new OutboxProperties(5, 50, 2, LONG_POLL_DELAY_MS, 30, 4, 12, 30));
        when(outboxPort.countPendingOutsidePartitionRange(2)).thenReturn(0L);
        when(outboxPort.findAndLockUnprocessed(anyInt(), anyInt())).thenReturn(List.of());

        outboxPoller.start();
        try {
            // One periodic task per configured partition; the unpartitioned
            // task must not be scheduled as well.
            assertEquals(2, queuedScheduledTaskCount());
            runQueuedScheduledTasks();
        } finally {
            forceStopRealExecutors();
        }

        verify(outboxPort, times(1)).findAndLockUnprocessed(50, 0);
        verify(outboxPort, times(1)).findAndLockUnprocessed(50, 1);
        verify(outboxPort, never()).findAndLockUnprocessed(50, 2);
    }

    @Test
    void shouldCountPartitionErrorsWhenPollCycleAborts() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, outboxProperties, registry);
        when(outboxPort.findAndLockUnprocessed(50, 0))
                .thenThrow(new IllegalStateException("database unavailable"));

        invokePartitionSafely(0);

        assertEquals(1.0,
                registry.counter("outbox.poll.partition_error", "partition", "0").count(), 0.0);
    }

    @Test
    void shouldIgnorePartitionErrorsWithoutAMeterRegistry() {
        when(outboxPort.findAndLockUnprocessed(50, 0))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> invokePartitionSafely(0));
    }

    @Test
    void shouldRecordTimeoutFailureWhenFutureWasCancelled() throws Exception {
        ExecutorService workers = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(workers).submit(any(Runnable.class));
        doThrow(new CancellationException("cancelled"))
                .when(future).get(anyLong(), any(TimeUnit.class));
        setField("workers", workers);
        EventEntry first = event("cancelled-1");
        EventEntry second = event("cancelled-2");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(first, second));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).recordFailure(eq(first),
                any(OutboxWorkerTimeoutException.class), eq(5));
        verify(outboxProcessor).recordFailure(eq(second),
                any(OutboxWorkerTimeoutException.class), eq(5));
    }

    @Test
    void shouldContinueBatchWhenAWorkerFutureFailsUnexpectedly() throws Exception {
        // ExecutionException means the worker's own failure bookkeeping threw;
        // the remaining events in the batch must still be waited for.
        ExecutorService workers = mock(ExecutorService.class);
        Future<?> first = mock(Future.class);
        Future<?> second = mock(Future.class);
        doReturn(first, second).when(workers).submit(any(Runnable.class));
        when(first.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new ExecutionException("worker failed", new RuntimeException("db down")));
        setField("workers", workers);
        EventEntry firstEvent = event("exec-1");
        EventEntry secondEvent = event("exec-2");
        when(outboxPort.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(firstEvent, secondEvent));

        assertDoesNotThrow(() -> outboxPoller.pollAndProcessEvents());

        verify(first).get(anyLong(), any(TimeUnit.class));
        verify(second).get(anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldNotAbortBatchWhenTimeoutFailureCannotBeRecorded() throws Exception {
        // A dead-letter/retry write failure during the deadline path must not
        // skip the rest of the batch; the event stays eligible for next poll.
        ExecutorService workers = mock(ExecutorService.class);
        Future<?> first = mock(Future.class);
        Future<?> second = mock(Future.class);
        doReturn(first, second).when(workers).submit(any(Runnable.class));
        when(first.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException("timed out"));
        when(second.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException("timed out"));
        when(outboxProcessor.recordFailure(any(EventEntry.class), any(Throwable.class), eq(5)))
                .thenThrow(new RuntimeException("failure store down"));
        setField("workers", workers);
        when(outboxPort.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(event("timeout-1"), event("timeout-2")));

        assertDoesNotThrow(() -> outboxPoller.pollAndProcessEvents());

        verify(outboxProcessor, times(2))
                .recordFailure(any(EventEntry.class), any(OutboxWorkerTimeoutException.class), eq(5));
    }

    @Test
    void shouldRecordOnlyOneFailureWhenAWorkerAlreadyWonTheClaim() throws Exception {
        // The worker records the handler failure before the deadline path
        // observes the timeout. The per-event claim must prevent a second
        // retry/dead-letter write (which would double-advance retryCount).
        ExecutorService workers = mock(ExecutorService.class);
        Future<?> timedOut = mock(Future.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return timedOut;
        }).when(workers).submit(any(Runnable.class));
        when(timedOut.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException("timed out"));
        when(outboxProcessor.recordFailure(any(EventEntry.class), any(Throwable.class), eq(5)))
                .thenReturn(true);
        doThrow(new RuntimeException("handler failed"))
                .when(outboxProcessor).processEvent(any(EventEntry.class));
        setField("workers", workers);
        when(outboxPort.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(event("claim-1"), event("claim-2")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(2))
                .recordFailure(any(EventEntry.class), any(Throwable.class), eq(5));
    }

    @Test
    void shouldRestoreInterruptWhenWaitingForAWorkerIsInterrupted() throws Exception {
        ExecutorService workers = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(workers).submit(any(Runnable.class));
        doThrow(new InterruptedException("stop"))
                .when(future).get(anyLong(), any(TimeUnit.class));
        setField("workers", workers);
        when(outboxPort.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(event("interrupt-1"), event("interrupt-2")));
        Thread.interrupted();

        outboxPoller.pollAndProcessEvents();

        assertTrue(Thread.interrupted());
        verify(outboxProcessor, never()).recordFailure(any(), any(), anyInt());
    }

    @Test
    void shouldReleaseFailureClaimWhenProcessorReportsNoOwnership() throws Exception {
        EventEntry event = event("claim-race");
        Set<String> claims = ConcurrentHashMap.newKeySet();
        when(outboxProcessor.recordFailure(eq(event), any(Throwable.class), eq(5)))
                .thenReturn(false);
        Method method = OutboxPoller.class.getDeclaredMethod(
                "recordFailureOnce", EventEntry.class, Throwable.class, int.class, Set.class);
        method.setAccessible(true);

        method.invoke(outboxPoller, event, new RuntimeException("failure"), 5, claims);

        assertFalse(claims.contains(event.id()));
    }

    @Test
    void shouldForceStopExecutorWhenGracefulWaitTimesOut() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.isShutdown()).thenReturn(false);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);
        setField("executor", executor);

        outboxPoller.stop();

        verify(executor).shutdown();
        verify(executor).shutdownNow();
    }

    @Test
    void shouldRestoreInterruptWhenExecutorWaitIsInterrupted() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.isShutdown()).thenReturn(false);
        when(executor.awaitTermination(5, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("stop"));
        setField("executor", executor);
        Thread.interrupted();

        outboxPoller.stop();

        assertTrue(Thread.interrupted());
        verify(executor).shutdownNow();
    }

    @Test
    void shouldForceStopWorkersWhenGracefulWaitTimesOut() throws Exception {
        ExecutorService workers = mock(ExecutorService.class);
        when(workers.isShutdown()).thenReturn(false);
        when(workers.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);
        setField("workers", workers);

        outboxPoller.stop();

        verify(workers).shutdown();
        verify(workers).shutdownNow();
    }

    @Test
    void shouldRestoreInterruptWhenWorkerWaitIsInterrupted() throws Exception {
        ExecutorService workers = mock(ExecutorService.class);
        when(workers.isShutdown()).thenReturn(false);
        when(workers.awaitTermination(5, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("stop"));
        setField("workers", workers);
        Thread.interrupted();

        outboxPoller.stop();

        assertTrue(Thread.interrupted());
        verify(workers).shutdownNow();
    }

    @Test
    void shouldCreateDaemonWorkerThreadsWithRoleSpecificNames() throws Exception {
        outboxPoller.start();
        try {
            Thread worker = createThreadOnNonDaemonCaller(
                    ((ThreadPoolExecutor) field("workers")).getThreadFactory());
            assertNotNull(worker);
            assertTrue(worker.isDaemon());
            assertTrue(worker.getName().startsWith("outbox-worker-"));
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldCreateDaemonPollerThreadsWithRoleSpecificNames() throws Exception {
        outboxPoller.start();
        try {
            Thread poller = createThreadOnNonDaemonCaller(
                    ((ScheduledThreadPoolExecutor) field("executor")).getThreadFactory());
            assertNotNull(poller);
            assertTrue(poller.isDaemon());
            assertTrue(poller.getName().startsWith("outbox-poller-"));
        } finally {
            forceStopRealExecutors();
        }
    }

    @Test
    void shouldCalculateAndApplyTheBatchDeadlineExactly() throws Exception {
        Method remaining = OutboxPoller.class.getDeclaredMethod(
                "remainingNanos", long.class, long.class);
        remaining.setAccessible(true);
        assertEquals(6L, ((Long) remaining.invoke(null, 10L, 4L)).longValue());
        assertEquals(-6L, ((Long) remaining.invoke(null, 4L, 10L)).longValue());

        Method exceeded = OutboxPoller.class.getDeclaredMethod(
                "deadlineExceeded", long.class);
        exceeded.setAccessible(true);
        assertTrue((Boolean) exceeded.invoke(null, 0L));
        assertFalse((Boolean) exceeded.invoke(null, 1L));
        assertTrue((Boolean) exceeded.invoke(null, -1L));
    }

    // PIT may run tests on a daemon thread; a factory that inherits the
    // caller's daemon flag would then look correct even without setDaemon.
    // Invoke the factory from a thread that is explicitly non-daemon so the
    // mutation is observable.
    private static Thread createThreadOnNonDaemonCaller(ThreadFactory factory)
            throws InterruptedException {
        Thread[] created = new Thread[1];
        Thread caller = new Thread(() -> created[0] = factory.newThread(() -> { }),
                "factory-caller");
        caller.setDaemon(false);
        caller.start();
        caller.join();
        return created[0];
    }

    private int queuedScheduledTaskCount() throws Exception {
        return ((ScheduledThreadPoolExecutor) field("executor")).getQueue().size();
    }

    private void runQueuedScheduledTasks() throws Exception {
        List<Runnable> queued =
                new ArrayList<>(((ScheduledThreadPoolExecutor) field("executor")).getQueue());
        for (Runnable task : queued) {
            task.run();
        }
    }

    private void forceStopRealExecutors() throws Exception {
        ScheduledExecutorService executor = (ScheduledExecutorService) field("executor");
        if (executor != null) {
            executor.shutdownNow();
        }
        ExecutorService pool = (ExecutorService) field("workers");
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private int connectionPoolSize(DataSource dataSource) throws Exception {
        OutboxPoller poller = new OutboxPoller(outboxPort, outboxProcessor,
                outboxProperties, null, dataSource);
        Method method = OutboxPoller.class.getDeclaredMethod("connectionPoolSize");
        method.setAccessible(true);
        return (int) method.invoke(poller);
    }

    private void invokePartitionSafely(int partition) throws Exception {
        Method method = OutboxPoller.class.getDeclaredMethod(
                "processPartitionSafely", int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(outboxPoller, partition, 50, 5);
    }

    private Object field(String name) throws Exception {
        var field = OutboxPoller.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(outboxPoller);
    }

    private void setField(String name, Object value) throws Exception {
        var field = OutboxPoller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(outboxPoller, value);
    }
}
