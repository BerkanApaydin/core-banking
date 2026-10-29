package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulerDataSourceConfigTest {

    private final SchedulerDataSourceConfig config = new SchedulerDataSourceConfig();

    @Test
    void shouldReusePrimaryJdbcTemplateWhenSchedulerDatasourceIsUnset() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SchedulerDataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcTemplate primary = new JdbcTemplate();

        SchedulerJdbcOperations operations = config.schedulerJdbcOperations(provider, primary);

        assertThat(operations.jdbcTemplate()).isSameAs(primary);
    }

    @Test
    void shouldReusePrimaryTransactionManagerWhenSchedulerDatasourceIsUnset() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SchedulerDataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PlatformTransactionManager primary = mock(PlatformTransactionManager.class);

        SchedulerTransactionManager transactionManager =
                config.schedulerTransactionManager(provider, primary);

        assertThat(transactionManager.transactionManager()).isSameAs(primary);
    }

    @Test
    void shouldWireSchedulerBeansWithPrimaryFallback() {
        JdbcTemplate primary = mock(JdbcTemplate.class);
        PlatformTransactionManager primaryTx = mock(PlatformTransactionManager.class);

        new ApplicationContextRunner()
                .withUserConfiguration(SchedulerDataSourceConfig.class)
                .withBean("jdbcTemplate", JdbcTemplate.class, () -> primary)
                .withBean("transactionManager", PlatformTransactionManager.class, () -> primaryTx)
                .withBean(TransactionProperties.class, () -> new TransactionProperties(30))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("schedulerJdbcOperations");
                    assertThat(context).hasBean("schedulerTransactionManager");
                    assertThat(context).hasBean("schedulerLock");
                    assertThat(context.getBean(SchedulerJdbcOperations.class).jdbcTemplate())
                            .isSameAs(primary);
                    assertThat(context.getBean(SchedulerTransactionManager.class).transactionManager())
                            .isSameAs(primaryTx);
                });
    }
}
