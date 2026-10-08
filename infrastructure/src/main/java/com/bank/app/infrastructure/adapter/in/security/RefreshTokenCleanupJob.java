package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Hygiene for server-side refresh-token sessions: drops expired rows
 * (revoked-and-expired, rotated-and-expired, abandoned). Expired rows are
 * never read (expiry is checked before revocation state), so deletion only
 * reclaims storage — the migration comment claiming "expired rows are deleted
 * by cleanup job" (V31) is finally wired.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenPort refreshTokens;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;

    public RefreshTokenCleanupJob(RefreshTokenPort refreshTokens) {
        this(refreshTokens, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public RefreshTokenCleanupJob(RefreshTokenPort refreshTokens,
                                  AdvisorySchedulerLock schedulerLock) {
        this(refreshTokens, schedulerLock, null);
    }

    public RefreshTokenCleanupJob(RefreshTokenPort refreshTokens,
                                  AdvisorySchedulerLock schedulerLock,
                                  ClockProviderPort clockProvider) {
        this.refreshTokens = refreshTokens;
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
    }

    @Scheduled(cron = "${app.security.refresh-token.cleanup-cron:0 0 4 * * *}")
    public void cleanExpired() {
        // K12/D5: single-flight across replicas (expired-row DELETE is
        // idempotent, but N pods must not all scan).
        schedulerLock.runIfLeader("refresh-token-cleanup", () -> {
            try {
                Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
                int deleted = refreshTokens.deleteExpiredBefore(LocalDateTime.now(clock));
                log.info("Refresh token cleanup completed: expiredRowsDeleted={}", deleted);
            } catch (RuntimeException e) {
                // Expired records are rejected by expiry checks; a missed cleanup
                // only costs storage and can be retried on the next schedule.
                log.warn("Refresh token cleanup failed: {}", e.getClass().getSimpleName());
            }
        });
    }
}
