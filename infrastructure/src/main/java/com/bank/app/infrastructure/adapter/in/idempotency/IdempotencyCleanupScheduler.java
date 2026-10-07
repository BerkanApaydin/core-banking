package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.infrastructure.adapter.in.config.IdempotencyProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.common.application.port.out.IdempotencyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    static final String LOCK_NAME = "idempotency-cleanup";

    private final IdempotencyPort idempotencyPort;
    private final IdempotencyProperties idempotencyProperties;
    private final AdvisorySchedulerLock schedulerLock;

    public IdempotencyCleanupScheduler(
            IdempotencyPort idempotencyPort,
            IdempotencyProperties idempotencyProperties) {
        this(idempotencyPort, idempotencyProperties, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public IdempotencyCleanupScheduler(
            IdempotencyPort idempotencyPort,
            IdempotencyProperties idempotencyProperties,
            AdvisorySchedulerLock schedulerLock) {
        this.idempotencyPort = idempotencyPort;
        this.idempotencyProperties = idempotencyProperties;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron}")
    public void cleanupExpiredKeys() {
        // K12/D5: single-flight across replicas. No @Transactional here — the
        // lock guard owns the transaction (a contended lock marks rollback-only,
        // which must not poison an outer transaction).
        schedulerLock.runIfLeader(LOCK_NAME, () -> {
            LocalDateTime threshold = LocalDateTime.now().minusHours(idempotencyProperties.expirationHours());
            log.info("Cleaning up idempotency keys created before: {}", threshold);
            int deletedCount = idempotencyPort.deleteExpired(threshold);
            log.info("Deleted {} expired idempotency keys.", deletedCount);
        });
    }
}
