package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = TestApplication.class)
@Import(IdempotencyPersistenceAdapterIntegrationTest.TestConfig.class)
class IdempotencyPersistenceAdapterIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager();
        }
    }

    private final IdempotencyPersistenceAdapter adapter;

    private final IdempotencyKeyJpaRepository repository;

    private final JdbcTemplate jdbcTemplate;

    private final PlatformTransactionManager transactionManager;

    @Autowired
    IdempotencyPersistenceAdapterIntegrationTest(IdempotencyPersistenceAdapter adapter,
            IdempotencyKeyJpaRepository repository, JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.adapter = adapter;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void shouldStoreOversizedBodyAsNullAndNonReplayable() {
        String key = "it-oversized";
        adapter.tryCreate(key, LocalDateTime.now());
        String oversized = "x".repeat(IdempotencyPersistenceAdapter.MAX_RESPONSE_BODY_CHARS + 1);

        adapter.markCompleted(key, oversized, 200);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT response_body, response_status, response_replayable "
                        + "FROM idempotency_keys WHERE key_value = ?", key);
        assertNull(row.get("response_body"));
        assertEquals(200, ((Number) row.get("response_status")).intValue());
        assertEquals(Boolean.FALSE, row.get("response_replayable"));
        assertFalse(adapter.findById(key).orElseThrow().replayable());
    }

    @Test
    @Transactional
    void shouldFlagResetFailedKeyAsReplayableAgain() {
        String key = "it-reset";
        adapter.tryCreate(key, LocalDateTime.now());
        adapter.markFailed(key);
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET response_body = 'old', response_replayable = FALSE "
                        + "WHERE key_value = ?", key);

        adapter.tryResetFailed(key, LocalDateTime.now());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT response_body, response_replayable FROM idempotency_keys WHERE key_value = ?", key);
        assertNull(row.get("response_body"));
        assertEquals(Boolean.TRUE, row.get("response_replayable"));
    }

    @Test
    void shouldCommitRetentionBatchEvenWhenTheOuterTransactionRollsBack() {
        // R-I05: each cleanup batch is REQUIRES_NEW. A later failure in the
        // scheduler-lock transaction must not resurrect rows already deleted
        // and committed by an earlier batch.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        LocalDateTime old = LocalDateTime.now().minusDays(60);
        tx.executeWithoutResult(status -> repository.save(
                new IdempotencyKeyJpaEntity("it-retention", "COMPLETED", "{}", 200, old)));

        tx.executeWithoutResult(status -> {
            adapter.deleteExpired(LocalDateTime.now());
            status.setRollbackOnly();
        });

        assertEquals(0, repository.count(),
                "the REQUIRES_NEW batch delete must survive the outer rollback");
    }
}
