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

    private final IdempotencyKeyJpaRepository repository;

    private final JdbcTemplate jdbc;

    @Autowired
    IdempotencyCleanupIntegrationTest(IdempotencyKeyJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

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

    @Test
    void removesOnlyExpiredTerminalHandlerKeys() {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        LocalDateTime expired = threshold.minusHours(1);

        String completedHandler = "outbox_handler_completed_" + suffix;
        String pendingHandler = "outbox_handler_pending_" + suffix;
        String completedHttp = "http_completed_" + suffix;
        insert(completedHandler, "COMPLETED", expired);
        insert(pendingHandler, "PENDING", expired);
        insert(completedHttp, "COMPLETED", expired);

        assertEquals(1, repository.deleteExpiredHandlerKeys(threshold));

        assertExists(completedHandler, false);
        assertExists(pendingHandler, true);
        assertExists(completedHttp, true);
    }

    @Test
    void tryInsertDerivesKeyKindFromPrefix() {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String httpKey = "http_" + suffix;
        String handlerKey = "outbox_handler_Test_" + suffix;

        assertEquals(1, repository.tryInsert(httpKey, "hash", now));
        assertEquals(1, repository.tryInsert(handlerKey, "hash", now));

        assertEquals("HTTP", jdbc.queryForObject(
                "SELECT key_kind FROM idempotency_keys WHERE key_value = ?", String.class, httpKey));
        assertEquals("HANDLER", jdbc.queryForObject(
                "SELECT key_kind FROM idempotency_keys WHERE key_value = ?", String.class, handlerKey));
    }

    private void insert(String key, String status, LocalDateTime createdAt) {
        // Same discriminator the production tryInsert computes: prefix match
        // on the literal, so the near-miss stays HTTP like in production.
        String kind = key.startsWith("outbox_handler_") ? "HANDLER" : "HTTP";
        jdbc.update("INSERT INTO idempotency_keys (key_value, status, created_at, key_kind) VALUES (?, ?, ?, ?)",
                key, status, createdAt, kind);
    }

    private void assertExists(String key, boolean expected) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key_value = ?",
                Integer.class, key);
        assertEquals(expected ? 1 : 0, count, key);
    }
}
