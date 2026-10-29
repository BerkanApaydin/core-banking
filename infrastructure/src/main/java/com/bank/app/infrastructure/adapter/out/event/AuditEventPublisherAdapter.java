package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuditEventPublisherAdapter implements AuditEventPort {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisherAdapter.class);

    private static final int DEFAULT_BEST_EFFORT_TIMEOUT_SECONDS = 30;

    private final SaveAuditLogPort saveAuditLogPort;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditRetryOutboxAdapter retryOutboxAdapter;
    private final MeterRegistry meterRegistry;
    private final TransactionBoundaryPort transactionBoundary;
    private final Executor bestEffortExecutor;

    @Autowired
    public AuditEventPublisherAdapter(SaveAuditLogPort saveAuditLogPort,
            ApplicationEventPublisher eventPublisher,
            AuditRetryOutboxAdapter retryOutboxAdapter,
            @Nullable MeterRegistry meterRegistry,
            TransactionBoundaryPort transactionBoundary,
            @Qualifier("auditBestEffortExecutor") Executor bestEffortExecutor) {
        this.saveAuditLogPort = saveAuditLogPort;
        this.eventPublisher = eventPublisher;
        this.retryOutboxAdapter = retryOutboxAdapter;
        this.meterRegistry = meterRegistry;
        this.transactionBoundary = Objects.requireNonNull(
                transactionBoundary, "TransactionBoundaryPort must not be null");
        this.bestEffortExecutor = Objects.requireNonNull(
                bestEffortExecutor, "Best-effort executor must not be null");
    }

    @Override
    public void publish(AuditEvent event) {
        // Mandatory audit persistence joins the caller's transaction when one
        // is active; a failed audit write must roll back a successful money
        // movement. Without a caller transaction it uses the adapter's own.
        persist(event);
        // ...then dispatch for AFTER_COMMIT observation (see AuditEventConsumer):
        // the consumed counter proves this seam is wired end-to-end. The row
        // itself is already durable, so the listener never writes twice.
        dispatch(event);
    }

    @Override
    public void publishBestEffort(AuditEvent event) {
        if (!transactionBoundary.isTransactionActive()) {
            writeBestEffort(event);
            return;
        }
        // A best-effort row must not join the caller's transaction: the auth
        // flow may already have thrown (stale refresh, failed MFA) and roll
        // back. Writing it in REQUIRES_NEW while that caller still holds its
        // connection would double connection demand and can deadlock a small
        // pool under concurrency. Submit after completion instead: the caller
        // never waits for a second connection, and the single-thread executor
        // serializes the audit transaction.
        transactionBoundary.runAfterCompletion(() -> submitBestEffort(event));
    }

    private void submitBestEffort(AuditEvent event) {
        try {
            bestEffortExecutor.execute(() -> writeBestEffort(event));
        } catch (RuntimeException rejected) {
            increment("audit.best_effort.rejected", event);
            log.error("Best-effort audit executor rejected event; audit row is lost: action={}, failureType={}",
                    event.action(), rejected.getClass().getSimpleName());
        }
    }

    private void writeBestEffort(AuditEvent event) {
        try {
            transactionBoundary.executeRequiresNew(() -> {
                persist(event);
                return null;
            }, DEFAULT_BEST_EFFORT_TIMEOUT_SECONDS);
        } catch (RuntimeException writeFailure) {
            log.warn("Best-effort audit write failed; queueing durable retry: action={}, failureType={}",
                    event.action(), writeFailure.getClass().getSimpleName());
            enqueueRetry(event);
            return;
        }
        try {
            dispatch(event);
        } catch (RuntimeException observationFailure) {
            // The row is already durable; a broken observer must not turn a
            // successful authentication action into an error or duplicate the
            // audit row through the retry queue.
            log.warn("Audit row persisted but observation dispatch failed: action={}, failureType={}",
                    event.action(), observationFailure.getClass().getSimpleName());
        }
    }

    private void persist(AuditEvent event) {
        saveAuditLogPort.save(AuditLog.create(event.username(),
                AuditAction.fromString(event.action()), event.details(), event.occurredAt(),
                event.actorUserId()));
    }

    private void dispatch(AuditEvent event) {
        eventPublisher.publishEvent(event);
    }

    private void enqueueRetry(AuditEvent event) {
        try {
            retryOutboxAdapter.enqueue(event);
            increment("audit.retry.enqueued", event);
        } catch (RuntimeException enqueueFailure) {
            increment("audit.retry.failed", event);
            log.error("Durable audit retry enqueue failed; audit row is lost: action={}, failureType={}",
                    event.action(), enqueueFailure.getClass().getSimpleName());
        }
    }

    private void increment(String metric, AuditEvent event) {
        if (meterRegistry == null) {
            return;
        }
        var counter = meterRegistry.counter(metric, "action", event.action());
        if (counter != null) {
            counter.increment();
        }
    }
}
