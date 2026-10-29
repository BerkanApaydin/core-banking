package com.bank.app.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Wrapper that keeps the optional scheduler pool out of the primary
 * {@code DataSource} bean type. Registering a second {@link DataSource} bean
 * directly would make Spring Boot's {@code DataSourceAutoConfiguration} back
 * off and remove the primary pool.
 */
public final class SchedulerDataSource implements AutoCloseable {

    private final HikariDataSource dataSource;

    SchedulerDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
