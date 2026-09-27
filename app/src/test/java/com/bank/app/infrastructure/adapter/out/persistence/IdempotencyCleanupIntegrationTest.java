package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the actual cleanup SQL against PostgreSQL and the migrated schema. */
class IdempotencyCleanupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyKeyJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void removesOnlyExpiredTerminalRequestsAndPreservesOutboxDedup() {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        LocalDateTime expired = threshold.minusHours(1);
        LocalDateTime recent = threshold.plusHours(1);

        String completedRequest = "http_completed_" + suffix;
        String failedRequest = "http_failed_" + suffix;
        String pendingRequest = "http_pending_" + suffix;
        String recentRequest = "http_recent_" + suffix;
        String pendingOutbox = "outbox_handler_pending_" + suffix;
        String completedOutbox = "outbox_handler_completed_" + suffix;
        String prefixNearMiss = "outboxXhandlerXcompleted_" + suffix;

        insert(completedRequest, "COMPLETED", expired);
        insert(failedRequest, "FAILED", expired);
        insert(pendingRequest, "PENDING", expired);
        insert(recentRequest, "COMPLETED", recent);
        insert(pendingOutbox, "PENDING", expired);
        insert(completedOutbox, "COMPLETED", expired);
        insert(prefixNearMiss, "COMPLETED", expired);

        assertEquals(3, repository.deleteExpiredTerminalRequests(threshold));

        assertExists(completedRequest, false);
        assertExists(failedRequest, false);
        assertExists(prefixNearMiss, false);
        assertExists(pendingRequest, true);
        assertExists(recentRequest, true);
        assertExists(pendingOutbox, true);
        assertExists(completedOutbox, true);
    }

    private void insert(String key, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO idempotency_keys (key_value, status, created_at) VALUES (?, ?, ?)",
                key, status, createdAt);
    }

    private void assertExists(String key, boolean expected) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key_value = ?",
                Integer.class, key);
        assertEquals(expected ? 1 : 0, count, key);
    }
}
