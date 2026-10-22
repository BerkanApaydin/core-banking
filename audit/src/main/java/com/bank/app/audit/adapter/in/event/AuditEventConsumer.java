package com.bank.app.audit.adapter.in.event;

import com.bank.app.audit.application.port.out.AuditFailurePort;
import com.bank.app.audit.application.port.out.AuditObservationPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditObservationPort observationPort;
    private final AuditFailurePort auditFailurePort;

    public AuditEventConsumer(AuditObservationPort observationPort, AuditFailurePort auditFailurePort) {
        this.observationPort = observationPort;
        this.auditFailurePort = auditFailurePort;
    }

    // Deliberately NOT @Transactional: this listener performs zero database
    // work (in-memory metrics only). Opening a REQUIRES_NEW transaction here
    // would borrow a second pooled connection while the committing thread
    // still holds its own — under load that pool-starves the workers into a
    // deadlock (seen with 10 threads on a 3-connection pool). If this listener
    // ever needs the database again, make the observation asynchronous
    // instead of transactional.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEvent event) {
        if (event == null) {
            // A producer bug must not escape an AFTER_COMMIT listener: the
            // business transaction already committed, so a thrown exception
            // turns a successful money movement into a 500 and the client
            // retries it. Nothing to observe, so drop it.
            log.error("Ignoring null audit event");
            return;
        }
        try {
            // The row was already persisted synchronously by the AuditEventPort
            // implementation inside the business transaction, so this listener
            // never writes: it only observes that the event completed the full
            // pipeline (metric-only by design, no duplicate rows possible).
            // Resolving the action still validates the string-to-enum mapping.
            AuditAction.fromString(event.action());
            observationPort.recordConsumed(event.action());
        } catch (Exception e) {
            log.error("Failed to observe audit event: action={}, failureType={}",
                    event.action(), e.getClass().getSimpleName());
            try {
                auditFailurePort.recordFailure(event.action(), e.getClass().getSimpleName());
            } catch (Exception metricFailure) {
                // The failure metric is best-effort: a broken metrics registry
                // must not become an exception escaping the listener.
                log.error("Failed to record audit failure metric: action={}", event.action(), metricFailure);
            }
        }
    }
}
