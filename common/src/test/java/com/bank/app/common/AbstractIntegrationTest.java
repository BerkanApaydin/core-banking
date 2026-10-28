package com.bank.app.common;

import jakarta.persistence.EntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
public abstract class AbstractIntegrationTest {

    static {
        TestDatabaseContainer.getInstance();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        var pg = TestDatabaseContainer.getInstance();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    /**
     * Test-only ledger cleanup: opts into the transaction-local maintenance
     * flag that the V65 append-only trigger requires for DELETE. Must be
     * called inside the same transaction that removes ledger rows.
     */
    protected void enableLedgerMaintenance(EntityManager entityManager) {
        entityManager.createNativeQuery("SELECT set_config('app.ledger_maintenance', 'on', true)")
                .getSingleResult();
    }
}
