package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.port.out.AuditChainVerificationPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled tamper-evidence check for the audit trail. The database owns the
 * hash-chain computation (V67); this job only asks for the first broken link
 * and publishes the result as metrics, so an operator sees a break before a
 * regulator or forensic review does.
 *
 * <p>No advisory lock: the underlying function reads one consistent snapshot,
 * and concurrent inserts are serialized by the audit_chain_state row lock. A
 * failure to run does not claim the chain is broken; the staleness alert
 * catches a job that stops executing.
 */
@Service
@ConditionalOnProperty(name = "app.audit.chain-verification-enabled",
        havingValue = "true", matchIfMissing = true)
public class AuditChainVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerificationScheduler.class);

    private final AuditChainVerificationPort verificationPort;
    private final ClockProviderPort clockProvider;
    private final AtomicInteger broken = new AtomicInteger();
    private final AtomicLong lastVerifiedEpochSeconds = new AtomicLong();

    public AuditChainVerificationScheduler(AuditChainVerificationPort verificationPort,
            ClockProviderPort clockProvider,
            MeterRegistry meterRegistry) {
        this.verificationPort = verificationPort;
        this.clockProvider = clockProvider;
        Gauge.builder("audit.chain.broken", broken, AtomicInteger::get)
                .description("1 when the audit hash chain has a broken link, 0 when healthy")
                .register(meterRegistry);
        Gauge.builder("audit.chain.last_verified_epoch_seconds", lastVerifiedEpochSeconds, AtomicLong::get)
                .description("Unix epoch second of the last completed audit chain verification")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${app.audit.chain-verification-cron:0 30 3 * * *}")
    public void verifyChain() {
        try {
            OptionalLong brokenSeq = verificationPort.findFirstBrokenChainSeq();
            if (brokenSeq.isPresent()) {
                broken.set(1);
                log.error("Audit hash chain is broken at chain_seq={}", brokenSeq.getAsLong());
            } else {
                broken.set(0);
                log.info("Audit hash chain verified");
            }
            lastVerifiedEpochSeconds.set(clockProvider.clock().instant().getEpochSecond());
        } catch (RuntimeException verificationFailure) {
            log.error("Audit chain verification failed: failureType={}",
                    verificationFailure.getClass().getSimpleName());
        }
    }
}
