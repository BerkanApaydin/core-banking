package com.bank.app.common.security;

import com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);
    private final SpringDataIdempotencyKeyRepo repo;

    public IdempotencyCleanupScheduler(SpringDataIdempotencyKeyRepo repo) {
        this.repo = repo;
    }

    // Runs every hour to clean up keys older than 24 hours
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        log.info("Cleaning up idempotency keys created before: {}", threshold);
        int deletedCount = repo.deleteByCreatedAtBefore(threshold);
        log.info("Deleted {} expired idempotency keys.", deletedCount);
    }
}
