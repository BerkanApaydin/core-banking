package com.bank.app.common.adapter.in.idempotency;

import com.bank.app.common.adapter.in.config.IdempotencyProperties;
import com.bank.app.common.application.port.out.IdempotencyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);
    private final IdempotencyPort idempotencyPort;
    private final IdempotencyProperties idempotencyProperties;

    public IdempotencyCleanupScheduler(
            IdempotencyPort idempotencyPort,
            IdempotencyProperties idempotencyProperties) {
        this.idempotencyPort = idempotencyPort;
        this.idempotencyProperties = idempotencyProperties;
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron}")
    @Transactional
    public void cleanupExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(idempotencyProperties.expirationHours());
        log.info("Cleaning up idempotency keys created before: {}", threshold);
        int deletedCount = idempotencyPort.deleteExpired(threshold);
        log.info("Deleted {} expired idempotency keys.", deletedCount);
    }
}
