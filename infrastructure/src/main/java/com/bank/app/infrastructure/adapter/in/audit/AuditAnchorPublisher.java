package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.dto.AuditChainHead;
import com.bank.app.audit.application.port.out.AuditAnchorStorePort;
import com.bank.app.audit.application.port.out.AuditChainHeadPort;
import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.out.audit.AuditAnchorSigner;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Exports a signed snapshot of the audit-chain head outside the database (F1).
 *
 * <p>The nightly verifier proves the chain is internally consistent; this job
 * proves a later database state is a continuation of the state an external
 * party already saw. It runs after the verifier, reads the singleton
 * {@code audit_chain_state}, checks the latest anchor's HMAC and prefix link,
 * and appends a new anchor only when the head advanced. If the database head is
 * behind the anchor, or the anchored row hash no longer matches a retained row,
 * the job publishes a mismatch alarm and refuses to re-anchor.
 *
 * <p>The sink is an append-only file by default. It must point at a WORM/object
 * lock mount for the anchor to survive a privileged database actor; the code
 * cannot make an ordinary filesystem immutable.
 */
@Service
@ConditionalOnProperty(name = "app.audit.anchor.enabled", havingValue = "true")
public class AuditAnchorPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditAnchorPublisher.class);

    static final String LOCK_NAME = "audit-anchor";
    static final String LAST_SUCCESS_GAUGE = "audit.anchor.last_success_epoch_seconds";
    static final String LAST_SEQ_GAUGE = "audit.anchor.last_seq";
    static final String PUBLISHED_COUNTER = "audit.anchor.published";
    static final String FAILURES_COUNTER = "audit.anchor.failures";
    static final String MISMATCHES_COUNTER = "audit.anchor.mismatches";

    private final AuditChainHeadPort headPort;
    private final AuditAnchorStorePort anchorStore;
    private final AuditAnchorSigner signer;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong lastSeq = new AtomicLong();
    private final Counter published;
    private final Counter failures;
    private final Counter mismatches;

    public AuditAnchorPublisher(AuditChainHeadPort headPort,
            AuditAnchorStorePort anchorStore,
            AuditAnchorProperties properties,
            AdvisorySchedulerLock schedulerLock,
            ClockProviderPort clockProvider,
            MeterRegistry meterRegistry) {
        this.headPort = headPort;
        this.anchorStore = anchorStore;
        this.signer = new AuditAnchorSigner(properties);
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
        this.published = meterRegistry.counter(PUBLISHED_COUNTER);
        this.failures = meterRegistry.counter(FAILURES_COUNTER);
        this.mismatches = meterRegistry.counter(MISMATCHES_COUNTER);
        Gauge.builder(LAST_SUCCESS_GAUGE, lastSuccessEpochSeconds, AtomicLong::get)
                .description("Unix epoch second of the last successful audit anchor check")
                .register(meterRegistry);
        Gauge.builder(LAST_SEQ_GAUGE, lastSeq, AtomicLong::get)
                .description("Audit chain sequence covered by the latest anchor")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${app.audit.anchor-cron:0 45 3 * * *}")
    public void publishAnchor() {
        schedulerLock.runIfLeader(LOCK_NAME, this::publishOnce);
    }

    private void publishOnce() {
        try {
            Optional<AuditChainHead> headOptional = headPort.findHead();
            if (headOptional.isEmpty()) {
                failures.increment();
                log.error("Audit anchor skipped: audit_chain_state singleton is missing");
                return;
            }
            AuditChainHead head = headOptional.get();
            Optional<AuditAnchor> latestOptional = anchorStore.latest();

            if (latestOptional.isPresent()) {
                AuditAnchor latest = latestOptional.get();
                if (!signer.verify(latest)) {
                    mismatch("signature invalid for keyId=" + latest.keyId());
                    return;
                }
                if (latest.lastSeq() > head.lastSeq()) {
                    mismatch("database head " + head.lastSeq()
                            + " is behind published anchor " + latest.lastSeq());
                    return;
                }
                if (latest.lastSeq() == head.lastSeq()) {
                    if (!latest.lastHash().equals(head.lastHash())) {
                        mismatch("hash mismatch at anchored sequence " + latest.lastSeq());
                        return;
                    }
                    markSuccess(head);
                    return;
                }
                if (!prefixStillMatches(latest)) {
                    return;
                }
            }

            Instant now = clockProvider.clock().instant();
            AuditAnchor anchor = signer.sign(head.lastSeq(), head.lastHash(), now);
            anchorStore.append(anchor);
            published.increment();
            markSuccess(head, now);
            log.info("Audit anchor published: lastSeq={}, keyId={}", head.lastSeq(), anchor.keyId());
        } catch (RuntimeException e) {
            failures.increment();
            log.error("Audit anchor publication failed: failureType={}",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * The anchor's lastSeq may be older than the retained prefix because
     * retention is allowed to prune it. If that row is still inside the
     * retained window, its hash must match; a missing or rewritten row is a
     * tamper signal even when the current head moved forward.
     */
    private boolean prefixStillMatches(AuditAnchor anchor) {
        if (anchor.lastSeq() == 0) {
            return true;
        }
        Optional<String> anchoredHash = headPort.findRowHash(anchor.lastSeq());
        if (anchoredHash.isPresent()) {
            if (!MessageDigest.isEqual(
                    anchoredHash.get().getBytes(StandardCharsets.US_ASCII),
                    anchor.lastHash().getBytes(StandardCharsets.US_ASCII))) {
                mismatch("retained prefix hash mismatch at sequence " + anchor.lastSeq());
                return false;
            }
            return true;
        }

        OptionalLong minRetained = headPort.findMinChainSeq();
        if (minRetained.isEmpty()) {
            mismatch("audit table is empty while the chain state is non-zero");
            return false;
        }
        if (anchor.lastSeq() < minRetained.getAsLong()) {
            log.info("Audit anchor prefix at sequence {} has been pruned by retention",
                    anchor.lastSeq());
            return true;
        }
        mismatch("anchored row " + anchor.lastSeq() + " is missing but not pruned");
        return false;
    }

    private void mismatch(String reason) {
        mismatches.increment();
        log.error("External audit anchor verification failed: {}", reason);
    }

    private void markSuccess(AuditChainHead head) {
        markSuccess(head, clockProvider.clock().instant());
    }

    private void markSuccess(AuditChainHead head, Instant now) {
        lastSuccessEpochSeconds.set(now.getEpochSecond());
        lastSeq.set(head.lastSeq());
    }
}
