package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenBlacklistCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistCleanupJob.class);

    private final TokenBlacklistPort blacklist;

    public TokenBlacklistCleanupJob(TokenBlacklistPort blacklist) {
        this.blacklist = blacklist;
    }

    @Scheduled(cron = "${app.security.token-blacklist.cleanup-cron:0 */5 * * * *}")
    public void cleanExpired() {
        try {
            blacklist.cleanExpired();
        } catch (RuntimeException e) {
            // Expired records are ignored by read queries; a missed cleanup
            // only costs storage and can be retried on the next schedule.
            log.warn("Token revocation cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
