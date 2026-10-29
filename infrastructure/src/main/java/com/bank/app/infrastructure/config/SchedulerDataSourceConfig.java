package com.bank.app.infrastructure.config;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Optional scheduler datasource/role wiring (F2).
 *
 * <p>Without {@code app.scheduler.datasource.url} every scheduler bean falls
 * back to the primary datasource. With it, the reconciliation job reads
 * {@code ledger_entries} through a separate read-only pool and takes its
 * advisory lock on that pool, so the runtime {@code bank_app} role never needs
 * the scheduler's connection identity.
 *
 * <p>The wrappers ({@link SchedulerDataSource}, {@link SchedulerJdbcOperations},
 * {@link SchedulerTransactionManager}) deliberately hide these beans from the
 * primary {@code DataSource}/{@code JdbcOperations}/{@code TransactionManager}
 * types: registering those types directly would suppress Boot's primary
 * auto-configuration for the rest of the application.
 */
@Configuration
@EnableConfigurationProperties(SchedulerDataSourceProperties.class)
public class SchedulerDataSourceConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${app.scheduler.datasource.url:}' != ''")
    public SchedulerDataSource schedulerDataSource(SchedulerDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("scheduler-pool");
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setMinimumIdle(1);
        config.setReadOnly(properties.readOnly());
        config.setAutoCommit(true);
        return new SchedulerDataSource(new HikariDataSource(config));
    }

    @Bean
    public SchedulerJdbcOperations schedulerJdbcOperations(
            ObjectProvider<SchedulerDataSource> schedulerDataSource,
            JdbcTemplate primaryJdbcTemplate) {
        SchedulerDataSource scheduler = schedulerDataSource.getIfAvailable();
        return new SchedulerJdbcOperations(scheduler != null
                ? new JdbcTemplate(scheduler.dataSource())
                : primaryJdbcTemplate);
    }

    @Bean
    public SchedulerTransactionManager schedulerTransactionManager(
            ObjectProvider<SchedulerDataSource> schedulerDataSource,
            PlatformTransactionManager primaryTransactionManager) {
        SchedulerDataSource scheduler = schedulerDataSource.getIfAvailable();
        return new SchedulerTransactionManager(scheduler != null
                ? new DataSourceTransactionManager(scheduler.dataSource())
                : primaryTransactionManager);
    }

    @Bean
    public AdvisorySchedulerLock schedulerLock(
            SchedulerJdbcOperations schedulerJdbc,
            SchedulerTransactionManager schedulerTransactionManager,
            TransactionProperties transactionProperties) {
        return new AdvisorySchedulerLock(schedulerJdbc.jdbcTemplate(),
                schedulerTransactionManager.transactionManager(), transactionProperties);
    }
}
