package com.bank.app.persistence;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.TestDatabaseContainer;
import com.bank.app.infrastructure.adapter.in.audit.LedgerReconciliationJob;
import com.bank.app.infrastructure.config.SchedulerDataSource;
import com.bank.app.infrastructure.config.SchedulerJdbcOperations;
import com.bank.app.infrastructure.config.SchedulerTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2 regression: when {@code app.scheduler.datasource.url} is set, the
 * reconciliation job must use the separate {@code bank_scheduler} login, not
 * the primary {@code bank_app} pool.
 */
@SpringBootTest
@DisplayName("Scheduler datasource")
class SchedulerDataSourceIntegrationTest extends AbstractSpringBootIntegrationTest {

    private static final String SCHEDULER_ROLE = "bank_scheduler";
    private static final String SCHEDULER_PASSWORD = "scheduler-datasource-test-password";

    static {
        var postgres = TestDatabaseContainer.getInstance();
        try {
            executeScript(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            execute(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(),
                    "ALTER ROLE " + SCHEDULER_ROLE + " PASSWORD '" + SCHEDULER_PASSWORD + "'");
        } catch (Exception e) {
            throw new IllegalStateException("failed to provision scheduler role", e);
        }
    }

    @DynamicPropertySource
    static void schedulerProperties(DynamicPropertyRegistry registry) {
        var postgres = TestDatabaseContainer.getInstance();
        registry.add("app.scheduler.datasource.url", postgres::getJdbcUrl);
        registry.add("app.scheduler.datasource.username", () -> SCHEDULER_ROLE);
        registry.add("app.scheduler.datasource.password", () -> SCHEDULER_PASSWORD);
        registry.add("app.scheduler.datasource.maximum-pool-size", () -> 2);
    }

    private final SchedulerDataSource schedulerDataSource;
    private final SchedulerJdbcOperations schedulerJdbc;
    private final SchedulerTransactionManager schedulerTransactionManager;
    private final LedgerReconciliationJob reconciliationJob;
    private final MeterRegistry meterRegistry;

    @Autowired
    SchedulerDataSourceIntegrationTest(SchedulerDataSource schedulerDataSource,
            SchedulerJdbcOperations schedulerJdbc,
            SchedulerTransactionManager schedulerTransactionManager,
            LedgerReconciliationJob reconciliationJob,
            MeterRegistry meterRegistry,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.schedulerDataSource = schedulerDataSource;
        this.schedulerJdbc = schedulerJdbc;
        this.schedulerTransactionManager = schedulerTransactionManager;
        this.reconciliationJob = reconciliationJob;
        this.meterRegistry = meterRegistry;
    }

    @Test
    @DisplayName("reconciliation reads through the separate bank_scheduler pool")
    void reconciliationUsesSchedulerRole() {
        assertThat(schedulerDataSource).isNotNull();
        assertThat(schedulerTransactionManager.transactionManager()).isNotNull();
        assertThat(schedulerJdbc.jdbcTemplate().queryForObject("SELECT current_user", String.class))
                .isEqualTo(SCHEDULER_ROLE);

        double runsBefore = counterValue("ledger.reconciliation.runs");
        reconciliationJob.reconcile();

        assertThat(counterValue("ledger.reconciliation.runs")).isGreaterThan(runsBefore);
        assertThat(meterRegistry.get("ledger.reconciliation.last_success_epoch_seconds")
                .gauge().value()).isPositive();
    }

    private double counterValue(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static void executeScript(String url, String user, String password) throws Exception {
        String simpleUrl = url + (url.contains("?") ? "&" : "?") + "preferQueryMode=simple";
        execute(simpleUrl, user, password, readScript());
    }

    private static void execute(String url, String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String readScript() throws Exception {
        Path direct = Path.of("scripts", "provision_least_privilege_roles.sql");
        Path parent = Path.of("..", "scripts", "provision_least_privilege_roles.sql");
        Path path = Files.exists(direct) ? direct : parent;
        assertThat(Files.exists(path))
                .as("provisioning script exists at %s", path.toAbsolutePath())
                .isTrue();
        return Files.readString(path);
    }
}
