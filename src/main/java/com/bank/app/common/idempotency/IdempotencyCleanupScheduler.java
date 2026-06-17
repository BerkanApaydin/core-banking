package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);
    private final SpringDataIdempotencyKeyRepo repo;
    private final int expirationHours;

    public IdempotencyCleanupScheduler(
            SpringDataIdempotencyKeyRepo repo,
            @Value("${app.idempotency.expiration-hours}") int expirationHours) {
        this.repo = repo;
        this.expirationHours = expirationHours;
    }

    // Runs based on configured cron to clean up keys older than configured expiration hours
    @Scheduled(cron = "${app.idempotency.cleanup-cron}")
    @Transactional
    public void cleanupExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(expirationHours);
        log.info("Cleaning up idempotency keys created before: {}", threshold);
        int deletedCount = repo.deleteByCreatedAtBefore(threshold);
        log.info("Deleted {} expired idempotency keys.", deletedCount);
    }
}
