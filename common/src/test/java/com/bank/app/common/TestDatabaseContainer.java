package com.bank.app.common;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

public final class TestDatabaseContainer {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("bank_db_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public static PostgreSQLContainer<?> getInstance() {
        return POSTGRES;
    }

    private TestDatabaseContainer() {}
}
