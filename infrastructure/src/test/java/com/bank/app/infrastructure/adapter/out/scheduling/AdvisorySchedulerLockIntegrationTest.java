package com.bank.app.infrastructure.adapter.out.scheduling;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-I03 failure injection against PostgreSQL: the lock query is made to abort
 * the transaction with a real division-by-zero statement, so the fail-open path
 * must roll back the poisoned lock transaction before running the work.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(AdvisorySchedulerLockIntegrationTest.TestConfig.class)
class AdvisorySchedulerLockIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager();
        }
    }

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    AdvisorySchedulerLockIntegrationTest(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @Test
    void shouldRunWorkOutsideTheTransactionAbortedByTheLockQuery() {
        AdvisorySchedulerLock lock = new AdvisorySchedulerLock(
                new AbortingLockJdbcTemplate(jdbc.getDataSource()),
                new TransactionTemplate(transactionManager));

        boolean ran = lock.runIfLeader("failure-injection", () ->
                assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class)));

        assertTrue(ran, "fail-open must report that the work ran");
    }

    /**
     * Executes a statement that raises PostgreSQL's division_by_zero, which
     * marks the current transaction aborted exactly like a lock-query failure
     * would, then propagates the resulting DataAccessException.
     */
    private static final class AbortingLockJdbcTemplate extends JdbcTemplate {

        private AbortingLockJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1/0");
                }
                return null;
            });
            throw new IllegalStateException("The injected lock query did not abort the transaction");
        }
    }
}
